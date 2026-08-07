import type { Dataset } from '../api/contracts';

interface DatasetListProps {
  datasets: Dataset[];
}

export function DatasetList({ datasets }: DatasetListProps) {
  return (
    <div className="table-scroll" role="region" tabIndex={0} aria-label="Scrollable Dataset table">
      <table className="data-table">
        <caption>Datasets</caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Description</th>
            <th scope="col">Dataset ID</th>
            <th scope="col">Created</th>
          </tr>
        </thead>
        <tbody>
          {datasets.map((dataset) => (
            <tr key={dataset.id}>
              <th scope="row">{dataset.name}</th>
              <td>{dataset.description ?? <span className="muted-text">No description</span>}</td>
              <td>
                <code>{dataset.id}</code>
              </td>
              <td>
                <time dateTime={dataset.createdAt}>{dataset.createdAt}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
