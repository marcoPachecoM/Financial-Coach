import { useEffect, useState } from 'react';
import type { CategorySummaryItem } from '../types/transaction';

interface CategorySpendingResponse {
  status: string;
  categories: CategorySummaryItem[];
}

interface UseSpendingCategoryDataResult {
  data: CategorySummaryItem[];
  loading: boolean;
  error: string | null;
}

export const useSpendingCategoryData = (
  connectionId: string,
  startDate?: string,
  endDate?: string
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
      params.set('connection_id', connectionId);

      if (startDate) params.set('start_date', startDate);
      if (endDate) params.set('end_date', endDate);

      try {
        const token = localStorage.getItem('access_token');
        
        // Ensure request hits host port if local Vite/Webpack proxy isn't active
        const apiBase = 'http://localhost:8080'; 
        const url = `${apiBase}/api/v1/transactions/spending-by-category?${params.toString()}`;

        const response = await fetch(url, {
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        });

        if (!response.ok) {
          throw new Error(`API error: ${response.status} ${response.statusText}`);
        }

        const json: CategorySpendingResponse = await response.json();

        if (!isMounted) return;

        // Ensure category array exists and items have numeric safety defaults
        const cleanCategories = (json.categories || []).map((cat) => ({
          category: cat.category || 'Other',
          amount: typeof cat.amount === 'number' ? cat.amount : 0,
          count: typeof cat.count === 'number' ? cat.count : 0,
          percentage: typeof cat.percentage === 'number' ? cat.percentage : 0,
        }));

        setData(cleanCategories);
      } catch (err) {
        if (!isMounted) return;
        const message = err instanceof Error ? err.message : 'Failed to fetch spending by category';
        console.error('Failed to fetch spending by category:', message);
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