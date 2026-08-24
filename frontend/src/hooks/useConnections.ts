import { useState, useEffect, useCallback } from "react";
import type { Connection } from "../types/connection";
import { getConnections } from "../services/connections";

export const useConnections = () => {
  const [connections, setConnections] = useState<Connection[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchConnections = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getConnections();
      setConnections(data ?? []);
    } catch (err: unknown) {
      console.error("Failed to fetch connections:", err);
      setError(
        err instanceof Error ? err.message : "Error fetching connections",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchConnections();
  }, [fetchConnections]);

  return {
    connections,
    loading,
    error,
    refetch: fetchConnections,
  };
};
