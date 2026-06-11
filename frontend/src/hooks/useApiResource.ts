import { useEffect, useState } from 'react';

export function useApiResource<T>(loader: () => Promise<T>, fallback: T) {
  const [data, setData] = useState<T>(fallback);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let ignore = false;

    loader()
      .then((result) => {
        if (!ignore) {
          setData(result);
          setError(null);
        }
      })
      .catch((cause: unknown) => {
        if (!ignore) {
          setData(fallback);
          setError(cause instanceof Error ? cause : new Error('Unknown API error'));
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [fallback, loader]);

  return { data, loading, error };
}
