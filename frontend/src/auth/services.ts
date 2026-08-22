import type {
  LoginPayload,
  LoginResponse,
  RefreshResponse,
} from "../types/auth";
import authApi from "./instance";

export const login = async (data: LoginPayload) => {
  const res = await authApi.post<LoginResponse>("/login", data);
  return res.data;
};

export const refreshToken = async () => {
  const res = await authApi.post<RefreshResponse>("/refresh");
  return res.data;
};

export const logout = async (): Promise<void> => {
  await authApi.post("/logout");
};
