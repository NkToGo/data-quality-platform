import type { AsyncResourceStatus } from '../hooks/useAsyncResource';
import type { ValidationRun } from '../api/contracts';
import { ValidationRunStatusBadge } from './ValidationRunStatusBadge';

interface ValidationRunMetadataProps {
  validationRun: ValidationRun;
  datasetName: string | null;
  datasetContextStatus: AsyncResourceStatus;
}

interface LifecycleTimeProps {
  value: string | null;
  absentLabel: string;
}

function LifecycleTime({ value, absentLabel }: LifecycleTimeProps) {
  return value === null ? (
    <span className="muted-text">{absentLabel}</span>
  ) : (
    <time dateTime={value}>{value}</time>
  );
}

export function ValidationRunMetadata({
  validationRun,
  datasetName,
  datasetContextStatus,
}: ValidationRunMetadataProps) {
  return (
    <section className="panel run-metadata" aria-labelledby="run-metadata-heading">
      <p className="eyebrow">Read-only details</p>
      <h2 id="run-metadata-heading">Validation Run</h2>
      <dl className="metadata-grid">
        <div>
          <dt>Run ID</dt>
          <dd>
            <code>{validationRun.id}</code>
          </dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>
            <ValidationRunStatusBadge status={validationRun.status} />
          </dd>
        </div>
        <div>
          <dt>Dataset</dt>
          <dd>
            {datasetName === null ? null : <span>{datasetName}</span>}
            <code className={datasetName === null ? undefined : 'secondary-id'}>
              {validationRun.datasetId}
            </code>
            {datasetName === null && datasetContextStatus === 'loading' ? (
              <span className="metadata-note">Dataset name is loading.</span>
            ) : null}
            {datasetName === null && datasetContextStatus !== 'loading' ? (
              <span className="metadata-note">Dataset name is unavailable.</span>
            ) : null}
          </dd>
        </div>
        <div>
          <dt>SourceFile ID</dt>
          <dd>
            <code>{validationRun.sourceFileId}</code>
          </dd>
        </div>
        <div>
          <dt>Profile ID</dt>
          <dd>
            <code>{validationRun.profileId}</code>
          </dd>
        </div>
        <div>
          <dt>Started at</dt>
          <dd>
            <LifecycleTime value={validationRun.startedAt} absentLabel="Not started" />
          </dd>
        </div>
        <div>
          <dt>Finished at</dt>
          <dd>
            <LifecycleTime value={validationRun.finishedAt} absentLabel="Not finished" />
          </dd>
        </div>
        {validationRun.failureReason === null ? null : (
          <div className="metadata-wide">
            <dt>Failure reason</dt>
            <dd>{validationRun.failureReason}</dd>
          </div>
        )}
      </dl>
    </section>
  );
}
