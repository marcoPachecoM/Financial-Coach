import { useEffect, useState } from "react";

/**
 * `value`, but only after it stopped changing for `delayMs`: typing "uber" in a filter sends one
 * request instead of four.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
