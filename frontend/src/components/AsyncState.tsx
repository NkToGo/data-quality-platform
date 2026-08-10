interface LoadingStateProps {
  message: string;
}

export function LoadingState({ message }: LoadingStateProps) {
  return (
    <div className="async-state" role="status" aria-live="polite">
      <p>{message}</p>
    </div>
  );
}

interface ErrorStateProps {
  title: string;
  message: string;
  retryLabel: string;
  onRetry: () => void;
}

export function ErrorState({ title, message, retryLabel, onRetry }: ErrorStateProps) {
  return (
    <div className="async-state async-state-error" role="alert">
      <h3>{title}</h3>
      <p>{message}</p>
      <button type="button" onClick={onRetry}>
        {retryLabel}
      </button>
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  message: string;
}

export function EmptyState({ title, message }: EmptyStateProps) {
  return (
    <div className="async-state empty-state">
      <h3>{title}</h3>
      <p>{message}</p>
    </div>
  );
}
