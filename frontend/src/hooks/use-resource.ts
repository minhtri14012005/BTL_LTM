import { useCallback, useEffect, useState } from "react";
export function useResource<T>(
  load: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{
    data?: T;
    error?: string;
    loading: boolean;
  }>({ loading: true });
  useEffect(() => {
    const controller = new AbortController();
    setState({ loading: true });
    load(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ data, loading: false });
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setState({
            error:
              error instanceof Error ? error.message : "Chưa tải được dữ liệu.",
            loading: false,
          });
      });
    return () => controller.abort();
    // Callers supply all dependencies used by load.
  }, [...deps, revision]);
  return { ...state, reload: useCallback(() => setRevision((n) => n + 1), []) };
}
