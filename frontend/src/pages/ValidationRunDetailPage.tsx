import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { getDatasets, getValidationIssues, getValidationRun } from '../api/client';
import type { ValidationRun } from '../api/contracts';
import { EmptyState, ErrorState, LoadingState } from '../components/AsyncState';
import { ValidationIssueList } from '../components/ValidationIssueList';
import { ValidationRunMetadata } from '../components/ValidationRunMetadata';
import { ValidationRunSummary } from '../components/ValidationRunSummary';
import { useAsyncResource } from '../hooks/useAsyncResource';

interface ValidationRunDetailPageProps {
  runId: string;
}

export function ValidationRunDetailPage({ runId }: ValidationRunDetailPageProps) {
  return <ValidationRunRequest key={runId} runId={runId} />;
}

function ValidationRunRequest({ runId }: ValidationRunDetailPageProps) {
  const loadValidationRun = useCallback(
    (signal: AbortSignal) => getValidationRun(runId, signal),
    [runId],
  );
  const validationRun = useAsyncResource(loadValidationRun);

  return (
    <div className="run-detail">
      <nav className="detail-navigation" aria-label="Validation Run navigation">
        <Link to="/">Back to dashboard</Link>
      </nav>

      {validationRun.status === 'loading' ? (
        <section className="panel" aria-labelledby="run-loading-heading">
          <h2 id="run-loading-heading">Validation Run</h2>
          <LoadingState message="Loading Validation Run…" />
        </section>
      ) : null}

      {validationRun.status === 'error' ? (
        <section className="panel" aria-labelledby="run-error-heading">
          <h2 id="run-error-heading">Validation Run</h2>
          <ErrorState
            title="Validation Run could not be loaded"
            message={validationRun.error?.message ?? 'An unexpected error occurred.'}
            retryLabel="Retry Validation Run"
            onRetry={validationRun.retry}
          />
        </section>
      ) : null}

      {validationRun.status === 'success' && validationRun.data !== null ? (
        <LoadedValidationRun key={validationRun.data.id} validationRun={validationRun.data} />
      ) : null}
    </div>
  );
}

function LoadedValidationRun({ validationRun }: { validationRun: ValidationRun }) {
  const loadIssues = useCallback(
    (signal: AbortSignal) => getValidationIssues(validationRun.id, signal),
    [validationRun.id],
  );
  const loadDatasets = useCallback((signal: AbortSignal) => getDatasets(signal), []);
  const issues = useAsyncResource(loadIssues);
  const datasets = useAsyncResource(loadDatasets);

  const datasetName = useMemo(() => {
    if (datasets.status !== 'success' || datasets.data === null) {
      return null;
    }

    return datasets.data.find((dataset) => dataset.id === validationRun.datasetId)?.name ?? null;
  }, [datasets.data, datasets.status, validationRun.datasetId]);

  return (
    <>
      <ValidationRunMetadata
        validationRun={validationRun}
        datasetName={datasetName}
        datasetContextStatus={datasets.status}
      />
      <ValidationRunSummary validationRun={validationRun} />
      <section className="panel run-issues" aria-labelledby="run-issues-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Persisted findings</p>
            <h2 id="run-issues-heading">Validation Issues</h2>
          </div>
          {issues.status === 'success' && issues.data !== null ? (
            <span className="count-badge">{issues.data.length}</span>
          ) : null}
        </div>

        {issues.status === 'loading' ? <LoadingState message="Loading Validation Issues…" /> : null}
        {issues.status === 'error' ? (
          <ErrorState
            title="Validation Issues could not be loaded"
            message={issues.error?.message ?? 'An unexpected error occurred.'}
            retryLabel="Retry Validation Issues"
            onRetry={issues.retry}
          />
        ) : null}
        {issues.status === 'success' && issues.data?.length === 0 ? (
          <EmptyState
            title="No persisted Issues"
            message="This Validation Run has no persisted Issues."
          />
        ) : null}
        {issues.status === 'success' && issues.data !== null && issues.data.length > 0 ? (
          <ValidationIssueList issues={issues.data} />
        ) : null}
      </section>
    </>
  );
}
