import type {
  LoginPayload,
  PasswordResetPayload,
  PasswordResetResponse,
  RegisterPayload,
  RegisterResponse,
  TokenPair,
} from "../types/auth";
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

/** Sign-up: 201 with a token pair and the 8 recovery codes (shown only now). */
export const register = async (data: RegisterPayload): Promise<RegisterResponse> => {
  const res = await authApi.post<RegisterResponse>("/register", data);
  return res.data;
};

/** Forgot password: one recovery code proves the account. Every session of the user ends. */
export const resetPassword = async (data: PasswordResetPayload): Promise<PasswordResetResponse> => {
  const res = await authApi.post<PasswordResetResponse>("/password-reset", data);
  return res.data;
};
