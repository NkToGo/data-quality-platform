import { useCallback, useEffect, useState } from 'react';

export type AsyncResourceStatus = 'loading' | 'success' | 'error';

export interface AsyncResource<T> {
  status: AsyncResourceStatus;
  data: T | null;
  error: Error | null;
  retry: () => void;
}

function asError(error: unknown): Error {
  return error instanceof Error ? error : new Error('An unexpected error occurred.');
}

export function useAsyncResource<T>(load: (signal: AbortSignal) => Promise<T>): AsyncResource<T> {
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<AsyncResourceStatus>('loading');
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    queueMicrotask(() => {
      if (controller.signal.aborted) {
        return;
      }

      setStatus('loading');
      setData(null);
      setError(null);

      void load(controller.signal).then(
        (loadedData) => {
          if (!controller.signal.aborted) {
            setData(loadedData);
            setStatus('success');
          }
        },
        (loadError: unknown) => {
          if (!controller.signal.aborted) {
            setError(asError(loadError));
            setStatus('error');
          }
        },
      );
    });

    return () => controller.abort();
  }, [attempt, load]);

  const retry = useCallback(() => {
    setStatus('loading');
    setData(null);
    setError(null);
    setAttempt((currentAttempt) => currentAttempt + 1);
  }, []);

  return { status, data, error, retry };
}
