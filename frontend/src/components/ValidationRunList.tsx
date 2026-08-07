import { Link } from 'react-router-dom';
import type { ValidationRun, ValidationRunStatus } from '../api/contracts';

interface ValidationRunListProps {
  validationRuns: ValidationRun[];
  datasetNames: ReadonlyMap<string, string>;
}

const STATUS_LABELS: Record<ValidationRunStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

export function ValidationRunList({ validationRuns, datasetNames }: ValidationRunListProps) {
  return (
    <div
      className="table-scroll"
      role="region"
      tabIndex={0}
      aria-label="Scrollable Validation Run table"
    >
      <table className="data-table validation-run-table">
        <caption>Validation Runs</caption>
        <thead>
          <tr>
            <th scope="col">Run ID</th>
            <th scope="col">Status</th>
            <th scope="col">Dataset</th>
            <th scope="col">Total rows</th>
            <th scope="col">Valid rows</th>
            <th scope="col">Invalid rows</th>
            <th scope="col">Issues</th>
          </tr>
        </thead>
        <tbody>
          {validationRuns.map((validationRun) => {
            const datasetName = datasetNames.get(validationRun.datasetId);

            return (
              <tr key={validationRun.id}>
                <th scope="row">
                  <Link to={`/runs/${validationRun.id}`}>{validationRun.id}</Link>
                </th>
                <td>
                  <span className={`run-status run-status-${validationRun.status.toLowerCase()}`}>
                    {STATUS_LABELS[validationRun.status]}
                  </span>
                </td>
                <td>
                  {datasetName === undefined ? null : <span>{datasetName}</span>}
                  <code className={datasetName === undefined ? undefined : 'secondary-id'}>
                    {validationRun.datasetId}
                  </code>
                </td>
                <td>{validationRun.totalRows}</td>
                <td>{validationRun.validRows}</td>
                <td>{validationRun.invalidRows}</td>
                <td>{validationRun.issueCount}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
