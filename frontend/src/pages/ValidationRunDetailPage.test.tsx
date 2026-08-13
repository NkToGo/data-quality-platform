import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Link, Route, Routes, useParams } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDatasets, getValidationIssues, getValidationRun } from '../api/client';
import type { Dataset, ValidationIssue, ValidationRun } from '../api/contracts';
import { datasetFixture, validationIssueFixture, validationRunFixture } from '../test/fixtures';
import { renderWithRouter } from '../test/renderWithRouter';
import { ValidationRunDetailPage } from './ValidationRunDetailPage';

vi.mock('../api/client', () => ({
  getDatasets: vi.fn(),
  getValidationIssues: vi.fn(),
  getValidationRunReport: vi.fn(),
  getValidationRun: vi.fn(),
}));

const getDatasetsMock = vi.mocked(getDatasets);
const getValidationIssuesMock = vi.mocked(getValidationIssues);
const getValidationRunMock = vi.mocked(getValidationRun);

const markupValue = '<img src=x onerror=alert(1)>';
const longObservedValue =
  `BEGIN-${'0123456789abcdef'.repeat(128)}` + '-<strong>literal markup</strong>-END';

const issues: ValidationIssue[] = [
  {
    ...validationIssueFixture,
    id: '11111111-1111-4111-8111-111111111111',
    rowNumber: 2,
    fieldName: 'email',
    severity: 'ERROR',
    message: 'Email is required.',
    observedValue: '',
  },
  {
    ...validationIssueFixture,
    id: '22222222-2222-4222-8222-222222222222',
    rowNumber: 3,
    fieldName: 'Email',
    ruleType: 'UNIQUENESS',
    severity: 'WARNING',
    message: 'Uppercase field warning.',
    observedValue: null,
  },
  {
    ...validationIssueFixture,
    id: '33333333-3333-4333-8333-333333333333',
    rowNumber: 4,
    fieldName: ' email ',
    ruleType: 'DATA_TYPE',
    severity: 'ERROR',
    message: 'Whitespace field error.',
    observedValue: '   ',
  },
  {
    ...validationIssueFixture,
    id: '44444444-4444-4444-8444-444444444444',
    rowNumber: 5,
    fieldName: 'email',
    ruleType: 'DATE_FORMAT',
    severity: 'WARNING',
    message: 'Unicode value warning.',
    observedValue: 'München 東京',
  },
  {
    ...validationIssueFixture,
    id: '55555555-5555-4555-8555-555555555555',
    rowNumber: 6,
    fieldName: 'name',
    ruleType: 'NUMERIC_RANGE',
    severity: 'ERROR',
    message: 'Markup-like value error.',
    observedValue: markupValue,
  },
];

const secondRun: ValidationRun = {
  ...validationRunFixture,
  id: '66666666-6666-4666-8666-666666666666',
  datasetId: '77777777-7777-4777-8777-777777777777',
  sourceFileId: '88888888-8888-4888-8888-888888888888',
  profileId: '99999999-9999-4999-8999-999999999999',
  totalRows: 4,
  validRows: 4,
  invalidRows: 0,
  issueCount: 1,
};

const secondIssue: ValidationIssue = {
  ...validationIssueFixture,
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  runId: secondRun.id,
  rowNumber: 2,
  fieldName: 'second-field',
  severity: 'WARNING',
  message: 'Second Run warning.',
  observedValue: 'second value',
};

function deferred<T>() {
  let resolvePromise: (value: T) => void = () => undefined;
  let rejectPromise: (reason?: unknown) => void = () => undefined;
  const promise = new Promise<T>((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });

  return { promise, resolve: resolvePromise, reject: rejectPromise };
}

function renderDetail(runId = validationRunFixture.id) {
  return renderWithRouter(<ValidationRunDetailPage runId={runId} />, `/runs/${runId}`);
}

function issueRows() {
  return within(screen.getByRole('table', { name: 'Persisted Validation Issues' }))
    .getAllByRole('row')
    .slice(1);
}

function RoutedDetail() {
  const { runId } = useParams();
  return runId === undefined ? null : <ValidationRunDetailPage runId={runId} />;
}

function RouteChangeHarness() {
  return (
    <>
      <Link to={`/runs/${secondRun.id}`}>Open second Run</Link>
      <Routes>
        <Route path="/runs/:runId" element={<RoutedDetail />} />
      </Routes>
    </>
  );
}

describe('ValidationRunDetailPage', () => {
  beforeEach(() => {
    getValidationRunMock.mockReset().mockResolvedValue(validationRunFixture);
    getValidationIssuesMock.mockReset().mockResolvedValue(issues);
    getDatasetsMock.mockReset().mockResolvedValue([datasetFixture]);
  });

  it('loads the Run first, then starts independent Issue and Dataset requests', async () => {
    const runRequest = deferred<ValidationRun>();
    getValidationRunMock.mockReturnValue(runRequest.promise);

    renderDetail();

    expect(await screen.findByText('Loading Validation Run…')).toBeInTheDocument();
    expect(getValidationIssuesMock).not.toHaveBeenCalled();
    expect(getDatasetsMock).not.toHaveBeenCalled();

    await act(async () => runRequest.resolve(validationRunFixture));

    expect(await screen.findByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
    expect(getValidationIssuesMock).toHaveBeenCalledOnce();
    expect(getValidationIssuesMock).toHaveBeenCalledWith(
      validationRunFixture.id,
      expect.any(AbortSignal),
    );
    expect(getDatasetsMock).toHaveBeenCalledOnce();
    expect(getDatasetsMock).toHaveBeenCalledWith(expect.any(AbortSignal));
  });

  it('renders exact Run metadata, Dataset context, timestamps, and persisted summary values', async () => {
    const run = {
      ...validationRunFixture,
      totalRows: 17,
      validRows: 12,
      invalidRows: 5,
      issueCount: 8,
    };
    getValidationRunMock.mockResolvedValue(run);

    renderDetail();

    await screen.findByText(run.id);
    await screen.findByText(datasetFixture.name);
    const metadata = screen.getByRole('region', { name: 'Validation Run' });
    expect(within(metadata).getByText(run.id)).toBeInTheDocument();
    expect(within(metadata).getByText('Completed')).toBeInTheDocument();
    expect(within(metadata).getByText(datasetFixture.name)).toBeInTheDocument();
    expect(within(metadata).getByText(run.datasetId)).toBeInTheDocument();
    expect(within(metadata).getByText(run.sourceFileId)).toBeInTheDocument();
    expect(within(metadata).getByText(run.profileId)).toBeInTheDocument();

    const startedAt = within(metadata).getByText(run.startedAt!);
    const finishedAt = within(metadata).getByText(run.finishedAt!);
    expect(startedAt.closest('time')).toHaveAttribute('datetime', run.startedAt);
    expect(finishedAt.closest('time')).toHaveAttribute('datetime', run.finishedAt);

    const summary = screen.getByRole('region', { name: 'Run summary' });
    expect(within(summary).getByText('17')).toBeInTheDocument();
    expect(within(summary).getByText('12')).toBeInTheDocument();
    expect(within(summary).getByText('5')).toBeInTheDocument();
    expect(within(summary).getByText('8')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Export report' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to dashboard' })).toHaveAttribute('href', '/');
  });

  it('presents nullable lifecycle values neutrally', async () => {
    const pendingRun: ValidationRun = {
      ...validationRunFixture,
      status: 'PENDING',
      totalRows: 0,
      validRows: 0,
      invalidRows: 0,
      issueCount: 0,
      startedAt: null,
      finishedAt: null,
    };
    getValidationRunMock.mockResolvedValueOnce(pendingRun);

    renderDetail();

    expect(await screen.findByText('Pending')).toBeInTheDocument();
    expect(screen.getByText('Not started')).toBeInTheDocument();
    expect(screen.getByText('Not finished')).toBeInTheDocument();
    expect(screen.queryByText(/background|actively processing/i)).not.toBeInTheDocument();
    expect(screen.queryByText('Failure reason')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download JSON report' })).toBeEnabled();
  });

  it('shows a persisted failure reason only when present', async () => {
    const failedRun: ValidationRun = {
      ...validationRunFixture,
      id: secondRun.id,
      status: 'FAILED',
      totalRows: 0,
      validRows: 0,
      invalidRows: 0,
      issueCount: 0,
      failureReason: 'CSV content could not be validated.',
    };
    getValidationRunMock.mockResolvedValue(failedRun);
    renderDetail(failedRun.id);

    expect(await screen.findByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('Failure reason')).toBeInTheDocument();
    expect(screen.getByText(failedRun.failureReason!)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download CSV report' })).toBeEnabled();
  });

  it('uses the Dataset UUID when no matching Dataset exists', async () => {
    getDatasetsMock.mockResolvedValue([]);

    renderDetail();

    expect(await screen.findByText('Dataset name is unavailable.')).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.datasetId)).toBeInTheDocument();
    expect(screen.queryByText(datasetFixture.name)).not.toBeInTheDocument();
  });

  it('keeps the Run usable when Dataset lookup fails', async () => {
    const datasetRequest = deferred<Dataset[]>();
    getDatasetsMock.mockReturnValue(datasetRequest.promise);

    renderDetail();

    await waitFor(() => expect(getDatasetsMock).toHaveBeenCalledOnce());
    expect(screen.getByText('Dataset name is loading.')).toBeInTheDocument();

    await act(async () => {
      datasetRequest.reject(new Error('Dataset context unavailable.'));
    });

    expect(await screen.findByText('Dataset name is unavailable.')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.datasetId)).toBeInTheDocument();
    expect(await screen.findByText(issues[0].message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download JSON report' })).toBeEnabled();
  });

  it('renders persisted Issues in API order and safely distinguishes observed values', async () => {
    renderDetail();

    await screen.findByRole('table', { name: 'Persisted Validation Issues' });
    expect(
      screen.getByRole('region', { name: 'Scrollable Validation Issue table' }),
    ).toHaveAttribute('tabindex', '0');
    const rows = issueRows();
    expect(rows).toHaveLength(issues.length);
    issues.forEach((issue, index) => {
      expect(within(rows[index]).getByText(issue.message)).toBeInTheDocument();
    });

    expect(screen.getByText('Empty string')).toBeInTheDocument();
    expect(screen.getByText('Not recorded')).toBeInTheDocument();
    const whitespaceValue = screen.getByLabelText('Whitespace-only value');
    expect(whitespaceValue).toHaveTextContent('   ', { normalizeWhitespace: false });
    expect(screen.getByText('München 東京')).toBeInTheDocument();
    expect(screen.getByText(markupValue)).toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
  });

  it('renders a long observed value completely as inert text inside its presentation', async () => {
    const longValueIssue: ValidationIssue = {
      ...validationIssueFixture,
      id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      message: 'Long observed value.',
      observedValue: longObservedValue,
    };
    getValidationIssuesMock.mockResolvedValue([longValueIssue]);

    renderDetail();

    const observedValue = await screen.findByText(longObservedValue, {
      exact: true,
      selector: 'code.observed-value',
    });
    expect(observedValue).toHaveClass('observed-value');
    expect(observedValue.closest('td')).toContainElement(observedValue);
    expect(observedValue.textContent).toBe(longObservedValue);
    expect(observedValue.childElementCount).toBe(0);
    expect(observedValue.querySelector('strong')).toBeNull();
  });

  it('filters by approved severity without refetching or reordering Issues', async () => {
    renderDetail();

    await screen.findByText(issues[0].message);
    const filters = screen.getByRole('group', { name: 'Filter Issues' });
    const filterResults = screen.getByRole('status');
    expect(filters).toHaveAttribute('aria-describedby', filterResults.id);
    expect(filterResults).toHaveTextContent(`Showing all ${issues.length} persisted Issues.`);

    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'WARNING' } });

    const rows = issueRows();
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText(issues[1].message)).toBeInTheDocument();
    expect(within(rows[1]).getByText(issues[3].message)).toBeInTheDocument();
    expect(screen.queryByText(issues[0].message)).not.toBeInTheDocument();
    expect(filterResults).toHaveTextContent(`Showing 2 of ${issues.length} persisted Issues.`);
    expect(getValidationIssuesMock).toHaveBeenCalledOnce();
  });

  it('uses exact case-sensitive and whitespace-sensitive field filters in first-seen order', async () => {
    renderDetail();

    await screen.findByText(issues[0].message);
    const fieldFilter = screen.getByLabelText('Field name');
    expect(
      within(fieldFilter)
        .getAllByRole('option')
        .map((option) => option.getAttribute('value')),
    ).toEqual(['', 'email', 'Email', ' email ', 'name']);
    expect(
      within(fieldFilter)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['All fields', '"email"', '"Email"', '" email "', '"name"']);

    fireEvent.change(fieldFilter, { target: { value: 'email' } });
    expect(screen.getByText(issues[0].message)).toBeInTheDocument();
    expect(screen.getByText(issues[3].message)).toBeInTheDocument();
    expect(screen.queryByText(issues[1].message)).not.toBeInTheDocument();
    expect(screen.queryByText(issues[2].message)).not.toBeInTheDocument();

    fireEvent.change(fieldFilter, { target: { value: ' email ' } });
    expect(screen.getByText(issues[2].message)).toBeInTheDocument();
    expect(screen.queryByText(issues[0].message)).not.toBeInTheDocument();
    expect(getValidationIssuesMock).toHaveBeenCalledOnce();
  });

  it('combines filters with AND, distinguishes no matches, and clears without refetching', async () => {
    renderDetail();

    await screen.findByText(issues[0].message);
    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'WARNING' } });
    fireEvent.change(screen.getByLabelText('Field name'), { target: { value: ' email ' } });

    expect(screen.getByText('No matching Issues')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      `Showing 0 of ${issues.length} persisted Issues.`,
    );
    expect(
      screen.queryByRole('table', { name: 'Persisted Validation Issues' }),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));

    expect(screen.getByLabelText('Severity')).toHaveValue('');
    expect(screen.getByLabelText('Field name')).toHaveValue('');
    expect(screen.getByRole('status')).toHaveTextContent(
      `Showing all ${issues.length} persisted Issues.`,
    );
    expect(issueRows()).toHaveLength(issues.length);
    expect(getValidationIssuesMock).toHaveBeenCalledOnce();
  });

  it('distinguishes a Run with no persisted Issues', async () => {
    getValidationIssuesMock.mockResolvedValue([]);

    renderDetail();

    expect(await screen.findByText('No persisted Issues')).toBeInTheDocument();
    expect(screen.queryByText('No matching Issues')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Severity')).not.toBeInTheDocument();
  });

  it('keeps Issue loading and retry states local to the loaded Run', async () => {
    getValidationIssuesMock
      .mockRejectedValueOnce(new Error('Issue service unavailable.'))
      .mockResolvedValueOnce(issues);

    renderDetail();

    const issueAlert = await screen.findByRole('alert');
    expect(
      within(issueAlert).getByRole('heading', {
        name: 'Validation Issues could not be loaded',
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.id)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download CSV report' })).toBeEnabled();

    fireEvent.click(within(issueAlert).getByRole('button', { name: 'Retry Validation Issues' }));

    expect(await screen.findByText(issues[0].message)).toBeInTheDocument();
    expect(getValidationIssuesMock).toHaveBeenCalledTimes(2);
    expect(getValidationRunMock).toHaveBeenCalledOnce();
    expect(getDatasetsMock).toHaveBeenCalledOnce();
  });

  it('shows Issue loading without hiding the Run summary', async () => {
    const issueRequest = deferred<ValidationIssue[]>();
    getValidationIssuesMock.mockReturnValue(issueRequest.promise);

    renderDetail();

    expect(await screen.findByText('Loading Validation Issues…')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Loading Validation Issues…');
    expect(screen.getByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
  });

  it('retries a failed Run before requesting dependent resources', async () => {
    getValidationRunMock
      .mockRejectedValueOnce(new Error('Run service unavailable.'))
      .mockResolvedValueOnce(validationRunFixture);

    renderDetail();

    expect(await screen.findByText('Validation Run could not be loaded')).toBeInTheDocument();
    expect(getValidationIssuesMock).not.toHaveBeenCalled();
    expect(getDatasetsMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Retry Validation Run' }));

    expect(await screen.findByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
    expect(getValidationRunMock).toHaveBeenCalledTimes(2);
    expect(getValidationIssuesMock).toHaveBeenCalledOnce();
    expect(getDatasetsMock).toHaveBeenCalledOnce();
  });

  it('resets filters when navigation selects a different Run', async () => {
    getValidationRunMock.mockImplementation((runId) =>
      Promise.resolve(runId === secondRun.id ? secondRun : validationRunFixture),
    );
    getValidationIssuesMock.mockImplementation((runId) =>
      Promise.resolve(runId === secondRun.id ? [secondIssue] : issues),
    );

    renderWithRouter(<RouteChangeHarness />, `/runs/${validationRunFixture.id}`);

    await screen.findByText(issues[0].message);
    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'ERROR' } });
    fireEvent.change(screen.getByLabelText('Field name'), { target: { value: 'email' } });

    fireEvent.click(screen.getByRole('link', { name: 'Open second Run' }));

    expect(await screen.findByText(secondIssue.message)).toBeInTheDocument();
    expect(screen.getByLabelText('Severity')).toHaveValue('');
    expect(screen.getByLabelText('Field name')).toHaveValue('');
    expect(getValidationIssuesMock).toHaveBeenCalledTimes(2);
  });

  it('aborts a superseded Run request and ignores its late result', async () => {
    const firstRequest = deferred<ValidationRun>();
    let firstSignal: AbortSignal | undefined;
    getValidationRunMock.mockImplementation((runId, signal) => {
      if (runId === validationRunFixture.id) {
        firstSignal = signal;
        return firstRequest.promise;
      }
      return Promise.resolve(secondRun);
    });
    getValidationIssuesMock.mockResolvedValue([secondIssue]);

    renderWithRouter(<RouteChangeHarness />, `/runs/${validationRunFixture.id}`);

    await screen.findByText('Loading Validation Run…');
    fireEvent.click(screen.getByRole('link', { name: 'Open second Run' }));

    expect(await screen.findByText(secondRun.id)).toBeInTheDocument();
    expect(firstSignal?.aborted).toBe(true);

    await act(async () => firstRequest.resolve(validationRunFixture));

    expect(screen.queryByText(validationRunFixture.id)).not.toBeInTheDocument();
    expect(screen.getByText(secondRun.id)).toBeInTheDocument();
    expect(getValidationIssuesMock).toHaveBeenCalledOnce();
    expect(getValidationIssuesMock).toHaveBeenCalledWith(secondRun.id, expect.any(AbortSignal));
  });

  it('aborts superseded Issues and prevents their late result from leaking into the next Run', async () => {
    const firstIssueRequest = deferred<ValidationIssue[]>();
    let firstIssueSignal: AbortSignal | undefined;
    getValidationRunMock.mockImplementation((runId) =>
      Promise.resolve(runId === secondRun.id ? secondRun : validationRunFixture),
    );
    getValidationIssuesMock.mockImplementation((runId, signal) => {
      if (runId === validationRunFixture.id) {
        firstIssueSignal = signal;
        return firstIssueRequest.promise;
      }
      return Promise.resolve([secondIssue]);
    });

    renderWithRouter(<RouteChangeHarness />, `/runs/${validationRunFixture.id}`);

    await screen.findByText('Loading Validation Issues…');
    fireEvent.click(screen.getByRole('link', { name: 'Open second Run' }));

    expect(await screen.findByText(secondIssue.message)).toBeInTheDocument();
    expect(firstIssueSignal?.aborted).toBe(true);

    await act(async () => firstIssueRequest.resolve(issues));

    expect(screen.queryByText(issues[0].message)).not.toBeInTheDocument();
    expect(screen.getByText(secondIssue.message)).toBeInTheDocument();
  });
});
