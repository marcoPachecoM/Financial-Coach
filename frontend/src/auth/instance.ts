import type { AxiosError } from "axios";
import { create } from "axios";
import { authStorage } from "./storage";

const authApi = create({
  baseURL: `${import.meta.env.VITE_API_URL}/auth`,
  headers: {
    "Content-Type": "application/json",
  },
});

authApi.interceptors.request.use(
  (config) => {
    if (
      config.url?.startsWith("/logout") ||
      config.url?.startsWith("/refresh")
    ) {
      const token = authStorage.getRefreshToken();

      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }

    return config;
  },
  (error) => Promise.reject(error),
);

authApi.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    return Promise.reject(error);
  },
);

export default authApi;
