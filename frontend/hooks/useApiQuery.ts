"use client";

import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "@/lib/api";

interface QueryState<T> {
  key: string | null;
  data: T | null;
  error: string | null;
}

/**
 * Minimal fetch-on-mount hook with refetch. Loading is derived: the state carries the key
 * of the request that produced it, so a new key (changed deps or refetch) means "loading"
 * without setting state inside the effect. Stale responses are discarded by key.
 */
export function useApiQuery<T>(fetcher: (signal: AbortSignal) => Promise<T>, deps: unknown[] = []) {
  const [version, setVersion] = useState(0);
  const key = JSON.stringify([...deps, version]);
  const [state, setState] = useState<QueryState<T>>({ key: null, data: null, error: null });

  useEffect(() => {
    const controller = new AbortController();
    fetcher(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ key, data, error: null });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof DOMException && error.name === "AbortError") return;
        setState((prev) => ({ key, data: prev.data, error: errorMessage(error) }));
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const refetch = useCallback(() => setVersion((v) => v + 1), []);
  const setData = useCallback((updater: (prev: T | null) => T | null) => {
    setState((prev) => ({ ...prev, data: updater(prev.data) }));
  }, []);

  return { data: state.data, error: state.key === key ? state.error : null, loading: state.key !== key, refetch, setData };
}
