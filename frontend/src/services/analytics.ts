import type {
  CategoryQueryParams,
  CategorySummaryItem,
} from "../types/transaction";
import api from "./instance";

export const getAnalyticsByCategory = async (
  params: CategoryQueryParams,
): Promise<CategorySummaryItem[]> => {
  const res = await api.get<CategorySummaryItem[]>("/analytics/by-category", {
    params,
  });
  console.log("API response for analytics by category:", res.data);
  return res.data;
};
