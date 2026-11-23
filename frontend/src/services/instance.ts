import { AxiosError, AxiosHeaders, create, type InternalAxiosRequestConfig } from "axios";

import { refreshTokens } from "../auth/services";
import { authStorage, SESSION_EXPIRED_EVENT } from "../auth/storage";
import type { ProblemDetail } from "../types/api";

/**
 * Client for every endpoint except /auth. Adds the access token and, when it has expired
 * (401 INVALID_TOKEN), refreshes the pair once and retries the request.
 */
const api = create({
  baseURL: `${import.meta.env.VITE_API_URL}`,
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = authStorage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Shared by every request that fails at the same time: only one refresh call per tab. */
let refreshInFlight: Promise<string> | null = null;

/**
 * Each refresh token works once, and reusing one makes the server close every session. So
 * refreshes are serialized across tabs (Web Locks API) and, if another tab already rotated the
 * tokens while this one waited, its new access token is used instead of refreshing again.
 */
const refreshAccessToken = (): Promise<string> => {
  if (!refreshInFlight) {
    const tokenBeforeWaiting = authStorage.getRefreshToken();
    const doRefresh = async (): Promise<string> => {
      const current = authStorage.getRefreshToken();
      if (current && current !== tokenBeforeWaiting) {
        return authStorage.getAccessToken()!;
      }
      const { access_token, refresh_token } = await refreshTokens();
      authStorage.updateTokens(access_token, refresh_token);
      return access_token;
    };

    const locked = navigator.locks
      ? navigator.locks.request("financial-coach-token-refresh", doRefresh)
      : doRefresh();
    refreshInFlight = locked.finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
};

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const request = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const expired = error.response?.status === 401 && error.response.data?.code === "INVALID_TOKEN";

    if (!request || !expired || request._retry || !authStorage.getRefreshToken()) {
      return Promise.reject(error);
    }
    request._retry = true;

    try {
      const accessToken = await refreshAccessToken();
      request.headers = AxiosHeaders.from(request.headers);
      request.headers.Authorization = `Bearer ${accessToken}`;
      return api(request);
    } catch (refreshError) {
      // The refresh token is expired, revoked or was reused: the user must log in again.
      authStorage.clear();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      return Promise.reject(refreshError);
    }
  },
);

/**
 * For requests made without axios (the copilot's streamed answer uses fetch): a fresh access
 * token after a 401 INVALID_TOKEN, sharing the same single refresh. If the refresh fails the
 * session ends, as for axios requests.
 */
export const refreshAccessTokenOrExpire = async (): Promise<string> => {
  try {
    return await refreshAccessToken();
  } catch (refreshError) {
    authStorage.clear();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    throw refreshError;
  }
};

export default api;
