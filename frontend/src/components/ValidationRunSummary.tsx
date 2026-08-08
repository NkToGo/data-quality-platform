import type { ValidationRun } from '../api/contracts';

interface ValidationRunSummaryProps {
  validationRun: ValidationRun;
}

export function ValidationRunSummary({ validationRun }: ValidationRunSummaryProps) {
  return (
    <section className="panel run-summary" aria-labelledby="run-summary-heading">
      <p className="eyebrow">Persisted result</p>
      <h2 id="run-summary-heading">Run summary</h2>
      <dl className="summary-grid">
        <div>
          <dt>Total rows</dt>
          <dd>{validationRun.totalRows}</dd>
        </div>
        <div>
          <dt>Valid rows</dt>
          <dd>{validationRun.validRows}</dd>
        </div>
        <div>
          <dt>Invalid rows</dt>
          <dd>{validationRun.invalidRows}</dd>
        </div>
        <div>
          <dt>Issue count</dt>
          <dd>{validationRun.issueCount}</dd>
        </div>
      </dl>
    </section>
  );
}
