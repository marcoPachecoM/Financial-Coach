import {
  AxiosError,
  create,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";

import { refreshToken } from "../auth/services";
import type { RefreshResponse } from "../types/auth";
import { authStorage } from "../auth/storage";

interface AxiosRetryConfig extends AxiosRequestConfig {
  _retry?: boolean;
}

let isRefreshing = false;

let failedQueue: {
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}[] = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(token!);
    }
  });

  failedQueue = [];
};

const api = create({
  baseURL: `${import.meta.env.VITE_API_URL}`,
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = authStorage.getAccessToken();

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error),
);

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRetryConfig | undefined;

    if (
      !originalRequest ||
      error.response?.status !== 401 ||
      originalRequest._retry
    ) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: (token: string) => {
            originalRequest.headers = {
              ...originalRequest.headers,
              Authorization: `Bearer ${token}`,
            };
            resolve(api(originalRequest));
          },
          reject,
        });
      });
    }

    isRefreshing = true;

    try {
      const { access_token, refresh_token }: RefreshResponse =
        await refreshToken();

      authStorage.updateTokens(access_token, refresh_token);

      processQueue(null, access_token);

      originalRequest.headers = {
        ...originalRequest.headers,
        Authorization: `Bearer ${access_token}`,
      };

      return api(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      authStorage.clear();

      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default api;
