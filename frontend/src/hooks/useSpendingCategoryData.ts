import { useEffect, useState } from "react";
import type { CategorySummaryItem } from "../types/transaction";
import { getAnalyticsByCategory } from "../services/analytics";

interface UseSpendingCategoryDataResult {
  data: CategorySummaryItem[];
  loading: boolean;
  error: string | null;
}

export const useSpendingCategoryData = (
  connectionId: string,
  startDate?: string,
  endDate?: string,
): UseSpendingCategoryDataResult => {
  const [data, setData] = useState<CategorySummaryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const fetchCategoryData = async () => {
      setLoading(true);
      setError(null);

      const params = new URLSearchParams();
      params.set("connection_id", connectionId);

      if (startDate) params.set("start_date", startDate);
      if (endDate) params.set("end_date", endDate);

      try {
        const categories = await getAnalyticsByCategory({
          connection_id: 1,
          group_by: "category",
          start_date: startDate,
          end_date: endDate,
        });

        // Ensure category array exists and items have numeric safety defaults
        const cleanCategories = (categories || []).map((cat) => ({
          category: cat.category || "Other",
          amount: !isNaN(Number(cat.amount)) ? Number(cat.amount) : 0,
          count: typeof cat.count === "number" ? cat.count : 0,
          percentage: !isNaN(Number(cat.percentage))
            ? Number(cat.percentage)
            : 0,
        }));

        console.log("Fetched category data:", cleanCategories);
        setData(cleanCategories);
      } catch (err) {
        if (!isMounted) return;
        const message =
          err instanceof Error
            ? err.message
            : "Failed to fetch spending by category";
        console.error("Failed to fetch spending by category:", message);
        setError(message);
        setData([]);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchCategoryData();

    return () => {
      isMounted = false;
    };
  }, [connectionId, startDate, endDate]);

  return { data, loading, error };
};
