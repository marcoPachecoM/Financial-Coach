const ACCESS_TOKEN_KEY = "access_token";
const REFRESH_TOKEN_KEY = "refresh_token";

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

  updateAccessToken(accessToken: string) {
    if (localStorage.getItem("access_token")) {
      localStorage.setItem("access_token", accessToken);
      return;
    }

    if (sessionStorage.getItem("access_token")) {
      sessionStorage.setItem("access_token", accessToken);
    }
  },

  updateTokens(accessToken: string, refreshToken?: string) {
    this.updateAccessToken(accessToken);

    if (!refreshToken) {
      return;
    }

    if (localStorage.getItem("refresh_token")) {
      localStorage.setItem("refresh_token", refreshToken);
      return;
    }

    if (sessionStorage.getItem("refresh_token")) {
      sessionStorage.setItem("refresh_token", refreshToken);
    }
  },
  isAuthenticated() {
    return Boolean(this.getAccessToken());
  },
};
