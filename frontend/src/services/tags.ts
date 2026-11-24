import api from "./instance";
import type { ApiTagUsage } from "../types/api";

/** GET /tags: the user's tags in use, most used first; only one account's when `accountId` is given. */
export const getTags = async (accountId?: string): Promise<ApiTagUsage[]> => {
  const res = await api.get<ApiTagUsage[]>("/tags", { params: accountId ? { account_id: accountId } : {} });
  return res.data;
};
