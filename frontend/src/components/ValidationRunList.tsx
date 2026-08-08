import { Link } from 'react-router-dom';
import type { ValidationRun } from '../api/contracts';
import { ValidationRunStatusBadge } from './ValidationRunStatusBadge';

interface ValidationRunListProps {
  validationRuns: ValidationRun[];
  datasetNames: ReadonlyMap<string, string>;
}

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
                  <ValidationRunStatusBadge status={validationRun.status} />
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
