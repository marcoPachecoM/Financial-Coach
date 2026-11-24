import api from "./instance";
import { authStorage } from "../auth/storage";
import type { TokenPair } from "../types/auth";

/**
 * Changes the password. The server ends every session and returns a new pair, stored here so this
 * tab stays signed in.
 */
export const changePassword = async (currentPassword: string, newPassword: string): Promise<void> => {
  const res = await api.post<TokenPair>("/auth/change-password", {
    current_password: currentPassword,
    new_password: newPassword,
  });
  authStorage.updateTokens(res.data.access_token, res.data.refresh_token);
};

/** Unused recovery codes left. */
export const getRecoveryCodesRemaining = async (): Promise<number> => {
  const res = await api.get<{ remaining: number }>("/auth/recovery-codes");
  return res.data.remaining;
};

/** 8 new codes; the old ones stop working. */
export const regenerateRecoveryCodes = async (currentPassword: string): Promise<string[]> => {
  const res = await api.post<{ recovery_codes: string[] }>("/auth/recovery-codes", {
    current_password: currentPassword,
  });
  return res.data.recovery_codes;
};
