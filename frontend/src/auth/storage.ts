const ACCESS_TOKEN_KEY = "access_token";
const REFRESH_TOKEN_KEY = "refresh_token";

/** Fired when the session ends without the user logging out (refresh failed). */
export const SESSION_EXPIRED_EVENT = "auth:session-expired";

export const authStorage = {
  setTokens(accessToken: string, refreshToken: string, rememberMe: boolean) {
    this.clear();

    const storage = rememberMe ? localStorage : sessionStorage;

    storage.setItem(ACCESS_TOKEN_KEY, accessToken);
    storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },

  getAccessToken() {
    return (
      localStorage.getItem(ACCESS_TOKEN_KEY) ??
      sessionStorage.getItem(ACCESS_TOKEN_KEY)
    );
  },

  getRefreshToken() {
    return (
      localStorage.getItem(REFRESH_TOKEN_KEY) ??
      sessionStorage.getItem(REFRESH_TOKEN_KEY)
    );
  },

  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);

    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  },

  /** Replaces both tokens (refresh rotates them) in the storage chosen at login. */
  updateTokens(accessToken: string, refreshToken: string) {
    const storage = localStorage.getItem(REFRESH_TOKEN_KEY) !== null ? localStorage : sessionStorage;
    storage.setItem(ACCESS_TOKEN_KEY, accessToken);
    storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },

  isAuthenticated() {
    return Boolean(this.getRefreshToken());
  },
};
