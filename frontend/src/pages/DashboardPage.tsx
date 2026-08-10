import { useCallback, useMemo } from 'react';
import { getDatasets, getValidationRuns } from '../api/client';
import { DatasetList } from '../components/DatasetList';
import { EmptyState, ErrorState, LoadingState } from '../components/AsyncState';
import { ValidationRunList } from '../components/ValidationRunList';
import { useAsyncResource } from '../hooks/useAsyncResource';

export function DashboardPage() {
  const loadDatasets = useCallback((signal: AbortSignal) => getDatasets(signal), []);
  const loadValidationRuns = useCallback((signal: AbortSignal) => getValidationRuns(signal), []);
  const datasets = useAsyncResource(loadDatasets);
  const validationRuns = useAsyncResource(loadValidationRuns);

  const datasetNames = useMemo(() => {
    if (datasets.status !== 'success' || datasets.data === null) {
      return new Map<string, string>();
    }

    return new Map(datasets.data.map((dataset) => [dataset.id, dataset.name]));
  }, [datasets.data, datasets.status]);

  return (
    <div className="dashboard">
      <section className="panel dashboard-intro" aria-labelledby="dashboard-heading">
        <p className="eyebrow">Read-only overview</p>
        <h2 id="dashboard-heading">Data quality dashboard</h2>
        <p>
          Review persisted Datasets and Validation Runs. Collections remain in the order supplied by
          the backend.
        </p>
      </section>

      <section className="panel dashboard-section" aria-labelledby="datasets-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Catalog</p>
            <h2 id="datasets-heading">Datasets</h2>
          </div>
          {datasets.status === 'success' && datasets.data !== null ? (
            <span className="count-badge">{datasets.data.length}</span>
          ) : null}
        </div>

        {datasets.status === 'loading' ? <LoadingState message="Loading Datasets…" /> : null}
        {datasets.status === 'error' ? (
          <ErrorState
            title="Datasets could not be loaded"
            message={datasets.error?.message ?? 'An unexpected error occurred.'}
            retryLabel="Retry Datasets"
            onRetry={datasets.retry}
          />
        ) : null}
        {datasets.status === 'success' && datasets.data?.length === 0 ? (
          <EmptyState title="No Datasets" message="No Dataset metadata has been persisted yet." />
        ) : null}
        {datasets.status === 'success' && datasets.data !== null && datasets.data.length > 0 ? (
          <DatasetList datasets={datasets.data} />
        ) : null}
      </section>

      <section className="panel dashboard-section" aria-labelledby="validation-runs-heading">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Global collection</p>
            <h2 id="validation-runs-heading">Validation Runs</h2>
          </div>
          {validationRuns.status === 'success' && validationRuns.data !== null ? (
            <span className="count-badge">{validationRuns.data.length}</span>
          ) : null}
        </div>

        {validationRuns.status === 'loading' ? (
          <LoadingState message="Loading Validation Runs…" />
        ) : null}
        {validationRuns.status === 'error' ? (
          <ErrorState
            title="Validation Runs could not be loaded"
            message={validationRuns.error?.message ?? 'An unexpected error occurred.'}
            retryLabel="Retry Validation Runs"
            onRetry={validationRuns.retry}
          />
        ) : null}
        {validationRuns.status === 'success' && validationRuns.data?.length === 0 ? (
          <EmptyState
            title="No Validation Runs"
            message="No Validation Runs have been persisted yet."
          />
        ) : null}
        {validationRuns.status === 'success' &&
        validationRuns.data !== null &&
        validationRuns.data.length > 0 ? (
          <ValidationRunList validationRuns={validationRuns.data} datasetNames={datasetNames} />
        ) : null}
      </section>
    </div>
  );
}
