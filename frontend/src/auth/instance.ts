import { create } from "axios";
import { authStorage } from "./storage";

/**
 * Client for /auth. refresh and logout authenticate with the *refresh* token; login sends no
 * token. No retry logic here: a 401 from these endpoints means the session is over.
 */
const authApi = create({
  baseURL: `${import.meta.env.VITE_API_URL}/auth`,
});

authApi.interceptors.request.use((config) => {
  if (config.url?.startsWith("/logout") || config.url?.startsWith("/refresh")) {
    const token = authStorage.getRefreshToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

export default authApi;
