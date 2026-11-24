import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { getErrorMessage } from "../services/errors";

interface AsyncResult<T> {
  key: string;
  version: number;
  data?: T;
  error: string | null;
}

/**
 * Loads data whenever `key` changes (null = do not load yet). Responses that arrive after the
 * key changed are ignored, so switching accounts quickly never shows another account's data.
 * `refetch` reloads with the same key and keeps the old data visible meanwhile.
 */
export function useAsync<T>(load: () => Promise<T>, key: string | null) {
  const loadRef = useRef(load);
  useLayoutEffect(() => {
    loadRef.current = load;
  });

  const [version, setVersion] = useState(0);
  const [result, setResult] = useState<AsyncResult<T> | null>(null);

  useEffect(() => {
    if (key === null) return;
    let active = true;
    loadRef.current().then(
      (data) => active && setResult({ key, version, data, error: null }),
      (error: unknown) => active && setResult({ key, version, error: getErrorMessage(error) }),
    );
    return () => {
      active = false;
    };
  }, [key, version]);

  const refetch = useCallback(() => setVersion((v) => v + 1), []);
  const current = result?.key === key ? result : null;

  return {
    data: current?.data,
    error: current?.error ?? null,
    loading: key !== null && (current === null || current.version !== version),
    /** The last data loaded, even for an older key: keeps a table filled while the next page loads. */
    previousData: result?.data,
    refetch,
  };
}
