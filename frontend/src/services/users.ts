import api from "./instance";
import type { UserProfile } from "../types/api";

export const getCurrentUser = async (): Promise<UserProfile> => {
  const res = await api.get<{ user: UserProfile }>("/auth/me");
  return res.data.user;
};
