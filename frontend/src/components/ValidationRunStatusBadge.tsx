import type { ValidationRunStatus } from '../api/contracts';

const STATUS_LABELS: Record<ValidationRunStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

interface ValidationRunStatusBadgeProps {
  status: ValidationRunStatus;
}

export function ValidationRunStatusBadge({ status }: ValidationRunStatusBadgeProps) {
  return (
    <span className={`run-status run-status-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
  );
}
