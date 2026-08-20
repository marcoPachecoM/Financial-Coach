import { useState, useEffect, useCallback } from 'react';
import type { Transaction, TransactionsApiResponse } from '../types/transaction';

interface UseTransactionsOptions {
  connectionId?: string;
  startDate?: string;
  endDate?: string;
}

export const useTransactions = (options: UseTransactionsOptions = {}) => {
  const { connectionId = '1', startDate, endDate } = options;

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTransactions = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // Build query string dynamically based on active filters
      const params = new URLSearchParams();
      if (connectionId) params.append('connection_id', connectionId);
      if (startDate) params.append('start_date', startDate);
      if (endDate) params.append('end_date', endDate);

      const response = await fetch(
        `http://localhost:8080/api/v1/transactions?${params.toString()}`
      );

      if (!response.ok) {
        throw new Error(`HTTP Error ${response.status}: Failed to fetch transactions`);
      }

      const data: TransactionsApiResponse = await response.json();
      setTransactions(data.transactions || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred fetching transactions');
    } finally {
      setLoading(false);
    }
  }, [connectionId, startDate, endDate]);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  return {
    transactions,
    setTransactions,
    loading,
    error,
    refetch: fetchTransactions,
  };
};