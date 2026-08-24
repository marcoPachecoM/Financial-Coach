import { useCallback, useEffect, useState } from "react";

import type { Transaction } from "../types/transaction";
import { getTransactions } from "../services/transactions";

interface UseTransactionsOptions {
  connectionId?: string;
  startDate?: string;
  endDate?: string;
  size?: number;
}

export const useTransactions = (options: UseTransactionsOptions = {}) => {
  const { connectionId, startDate, endDate, size = 1000 } = options;

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTransactions = useCallback(async () => {
    if (!connectionId) return;

    try {
      setLoading(true);
      setError(null);

      const response = await getTransactions({
        connection_id: connectionId,
        start_date: startDate,
        end_date: endDate,
        page: 1,
        size,
      });

      setTransactions(response.content ?? []);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "An error occurred fetching transactions",
      );
    } finally {
      setLoading(false);
    }
  }, [connectionId, startDate, endDate, size]);

  useEffect(() => {
    void fetchTransactions();
  }, [fetchTransactions]);

  return {
    transactions,
    setTransactions,
    loading,
    error,
    refetch: fetchTransactions,
  };
};
