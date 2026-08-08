import { useMemo, useState } from 'react';
import {
  validationIssueSeverities,
  type ValidationIssue,
  type ValidationIssueSeverity,
} from '../api/contracts';
import { EmptyState } from './AsyncState';

interface ValidationIssueListProps {
  issues: ValidationIssue[];
}

function fieldOptionLabel(fieldName: string) {
  return JSON.stringify(fieldName) ?? fieldName;
}

function ObservedValue({ value }: { value: string | null }) {
  if (value === null) {
    return <span className="muted-text">Not recorded</span>;
  }

  if (value.length === 0) {
    return <span className="muted-text">Empty string</span>;
  }

  const whitespaceOnly = value.trim().length === 0;
  return (
    <code
      className="observed-value"
      aria-label={whitespaceOnly ? 'Whitespace-only value' : undefined}
    >
      {value}
    </code>
  );
}

export function ValidationIssueList({ issues }: ValidationIssueListProps) {
  const [severity, setSeverity] = useState<ValidationIssueSeverity | ''>('');
  const [fieldName, setFieldName] = useState('');

  const fieldNames = useMemo(() => {
    const seen = new Set<string>();
    const orderedFields: string[] = [];

    for (const issue of issues) {
      if (!seen.has(issue.fieldName)) {
        seen.add(issue.fieldName);
        orderedFields.push(issue.fieldName);
      }
    }

    return orderedFields;
  }, [issues]);

  const filteredIssues = useMemo(
    () =>
      issues.filter(
        (issue) =>
          (severity === '' || issue.severity === severity) &&
          (fieldName === '' || issue.fieldName === fieldName),
      ),
    [fieldName, issues, severity],
  );

  const filtersActive = severity !== '' || fieldName !== '';
  const clearFilters = () => {
    setSeverity('');
    setFieldName('');
  };

  return (
    <>
      <fieldset className="issue-filters">
        <legend>Filter Issues</legend>
        <label>
          Severity
          <select
            value={severity}
            onChange={(event) => setSeverity(event.target.value as ValidationIssueSeverity | '')}
          >
            <option value="">All severities</option>
            {validationIssueSeverities.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <label>
          Field name
          <select value={fieldName} onChange={(event) => setFieldName(event.target.value)}>
            <option value="">All fields</option>
            {fieldNames.map((option) => (
              <option key={option} value={option}>
                {fieldOptionLabel(option)}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className="secondary-action"
          onClick={clearFilters}
          disabled={!filtersActive}
        >
          Clear filters
        </button>
      </fieldset>

      {filteredIssues.length === 0 ? (
        <EmptyState
          title="No matching Issues"
          message="Persisted Issues exist, but none match the active filters."
        />
      ) : (
        <div
          className="table-scroll"
          role="region"
          tabIndex={0}
          aria-label="Scrollable Validation Issue table"
        >
          <table className="data-table validation-issue-table">
            <caption>Persisted Validation Issues</caption>
            <thead>
              <tr>
                <th scope="col">Row</th>
                <th scope="col">Field</th>
                <th scope="col">Rule type</th>
                <th scope="col">Severity</th>
                <th scope="col">Message</th>
                <th scope="col">Observed value</th>
              </tr>
            </thead>
            <tbody>
              {filteredIssues.map((issue) => (
                <tr key={issue.id}>
                  <th scope="row">{issue.rowNumber}</th>
                  <td>
                    <code className="preserve-whitespace">{issue.fieldName}</code>
                  </td>
                  <td>{issue.ruleType}</td>
                  <td>
                    <span
                      className={`issue-severity issue-severity-${issue.severity.toLowerCase()}`}
                    >
                      {issue.severity}
                    </span>
                  </td>
                  <td>{issue.message}</td>
                  <td>
                    <ObservedValue value={issue.observedValue} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
