import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useAsyncResource } from './useAsyncResource';

type Loader = (signal: AbortSignal) => Promise<string>;

describe('useAsyncResource', () => {
  it('returns to a clean loading state when its loader changes', async () => {
    const firstLoader = vi.fn<Loader>().mockResolvedValue('first result');
    let resolveSecond: (value: string) => void = () => undefined;
    const secondLoader = vi.fn<Loader>(
      () =>
        new Promise<string>((resolve) => {
          resolveSecond = resolve;
        }),
    );

    const { result, rerender } = renderHook(
      ({ loader }: { loader: Loader }) => useAsyncResource(loader),
      { initialProps: { loader: firstLoader } },
    );

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.data).toBe('first result');

    rerender({ loader: secondLoader });

    await waitFor(() => expect(result.current.status).toBe('loading'));
    expect(result.current.data).toBeNull();
    expect(result.current.error).toBeNull();

    act(() => resolveSecond('second result'));

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.data).toBe('second result');
  });

  it('aborts its active request when unmounted', async () => {
    let requestSignal: AbortSignal | undefined;
    const loader = vi.fn<Loader>(
      (signal) =>
        new Promise<string>(() => {
          requestSignal = signal;
        }),
    );

    const { unmount } = renderHook(() => useAsyncResource(loader));

    await waitFor(() => expect(loader).toHaveBeenCalledOnce());
    unmount();

    expect(requestSignal?.aborted).toBe(true);
  });

  it('prevents a superseded request from overwriting the newer resource state', async () => {
    let firstRequestSignal: AbortSignal | undefined;
    let resolveFirst: (value: string) => void = () => undefined;
    const firstLoader = vi.fn<Loader>(
      (signal) =>
        new Promise<string>((resolve) => {
          firstRequestSignal = signal;
          resolveFirst = resolve;
        }),
    );
    const secondLoader = vi.fn<Loader>().mockResolvedValue('newer result');

    const { result, rerender } = renderHook(
      ({ loader }: { loader: Loader }) => useAsyncResource(loader),
      { initialProps: { loader: firstLoader } },
    );

    await waitFor(() => expect(firstLoader).toHaveBeenCalledOnce());

    rerender({ loader: secondLoader });

    await waitFor(() => expect(secondLoader).toHaveBeenCalledOnce());
    expect(firstRequestSignal?.aborted).toBe(true);
    await waitFor(() => expect(result.current.data).toBe('newer result'));

    await act(async () => {
      resolveFirst('superseded result');
      await Promise.resolve();
    });

    expect(result.current.status).toBe('success');
    expect(result.current.data).toBe('newer result');
  });
});
