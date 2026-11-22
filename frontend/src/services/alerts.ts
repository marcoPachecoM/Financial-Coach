import api from "./instance";
import type { ApiAlert } from "../types/api";

/** month: "YYYY-MM"; omitted means the current month (server time). Most severe first. */
export const getAlerts = async (accountId: string, month?: string): Promise<ApiAlert[]> => {
  const res = await api.get<ApiAlert[]>(`/accounts/${accountId}/alerts`, {
    params: month ? { month } : undefined,
  });
  return res.data;
};
