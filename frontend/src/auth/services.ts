import type { LoginPayload, TokenPair } from "../types/auth";
import authApi from "./instance";

export const login = async (data: LoginPayload): Promise<TokenPair> => {
  const res = await authApi.post<TokenPair>("/login", data);
  return res.data;
};

/** Rotation: the refresh token sent stops working; the response carries a new one. */
export const refreshTokens = async (): Promise<TokenPair> => {
  const res = await authApi.post<TokenPair>("/refresh");
  return res.data;
};

export const logout = async (): Promise<void> => {
  await authApi.post("/logout");
};
