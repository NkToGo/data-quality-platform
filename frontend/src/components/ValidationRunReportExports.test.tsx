import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getValidationRunReport } from '../api/client';
import { saveValidationRunReport } from '../downloads/saveValidationRunReport';
import { validationRunFixture } from '../test/fixtures';
import { ValidationRunReportExports } from './ValidationRunReportExports';

vi.mock('../api/client', () => ({
  getValidationRunReport: vi.fn(),
}));

vi.mock('../downloads/saveValidationRunReport', () => ({
  saveValidationRunReport: vi.fn(),
}));

const getValidationRunReportMock = vi.mocked(getValidationRunReport);
const saveValidationRunReportMock = vi.mocked(saveValidationRunReport);

function deferred<T>() {
  let resolvePromise: (value: T) => void = () => undefined;
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve;
  });

  return { promise, resolve: resolvePromise };
}

describe('ValidationRunReportExports', () => {
  beforeEach(() => {
    getValidationRunReportMock.mockReset();
    saveValidationRunReportMock
      .mockReset()
      .mockReturnValue(`validation-run-${validationRunFixture.id}-report.json`);
  });

  it('downloads one JSON snapshot at a time and announces that it started', async () => {
    const reportRequest = deferred<Blob>();
    const report = new Blob(['{"status":"COMPLETED"}'], { type: 'application/json' });
    getValidationRunReportMock.mockReturnValue(reportRequest.promise);

    render(<ValidationRunReportExports runId={validationRunFixture.id} />);

    expect(screen.getByRole('heading', { name: 'Export report' })).toBeInTheDocument();
    expect(
      screen.getByText(/available for Pending, Processing, Completed, and Failed Runs/),
    ).toBeInTheDocument();
    expect(screen.getByText(/current persisted state and may be incomplete/)).toBeInTheDocument();
    expect(screen.getByText(/filters affect only the table below/i)).toBeInTheDocument();

    const formats = screen.getByRole('group', { name: 'Report download formats' });
    const jsonButton = within(formats).getByRole('button', { name: 'Download JSON report' });
    const csvButton = within(formats).getByRole('button', { name: 'Download CSV report' });
    jsonButton.focus();
    fireEvent.click(jsonButton);

    await waitFor(() =>
      expect(getValidationRunReportMock).toHaveBeenCalledWith(
        validationRunFixture.id,
        'json',
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByRole('status')).toHaveTextContent('Preparing JSON report download…');
    expect(jsonButton).toBeDisabled();
    expect(jsonButton).toHaveAttribute('aria-busy', 'true');
    expect(csvButton).toBeDisabled();
    fireEvent.click(csvButton);
    expect(getValidationRunReportMock).toHaveBeenCalledOnce();

    await act(async () => reportRequest.resolve(report));

    expect(saveValidationRunReportMock).toHaveBeenCalledWith(
      report,
      validationRunFixture.id,
      'json',
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      `validation-run-${validationRunFixture.id}-report.json download started.`,
    );
    expect(jsonButton).toBeEnabled();
    expect(csvButton).toBeEnabled();
    expect(jsonButton).toHaveFocus();
  });

  it('shows a local format-specific error and retries the same report', async () => {
    const report = new Blob(['rowNumber,fieldName\r\n'], { type: 'text/csv' });
    const retryRequest = deferred<Blob>();
    getValidationRunReportMock
      .mockRejectedValueOnce(new Error('The report endpoint could not be reached.'))
      .mockReturnValueOnce(retryRequest.promise);
    saveValidationRunReportMock.mockReturnValue(
      `validation-run-${validationRunFixture.id}-report.csv`,
    );

    render(<ValidationRunReportExports runId={validationRunFixture.id} />);
    fireEvent.click(screen.getByRole('button', { name: 'Download CSV report' }));

    const alert = await screen.findByRole('alert');
    expect(
      within(alert).getByRole('heading', { name: 'CSV report could not be downloaded' }),
    ).toBeInTheDocument();
    expect(alert).toHaveTextContent('The report endpoint could not be reached.');

    const retryButton = within(alert).getByRole('button', {
      name: 'Retry CSV report download',
    });
    retryButton.focus();
    fireEvent.click(retryButton);

    const csvButton = screen.getByRole('button', { name: 'Download CSV report' });
    expect(await screen.findByRole('status')).toHaveTextContent('Preparing CSV report download');
    expect(csvButton).toHaveFocus();

    await act(async () => retryRequest.resolve(report));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('download started.'));
    expect(getValidationRunReportMock).toHaveBeenCalledTimes(2);
    expect(getValidationRunReportMock.mock.calls.map((call) => call[1])).toEqual(['csv', 'csv']);
    expect(saveValidationRunReportMock).toHaveBeenCalledWith(
      report,
      validationRunFixture.id,
      'csv',
    );
    expect(csvButton).toHaveFocus();
  });

  it('aborts an active report request when the exports panel unmounts', async () => {
    const reportRequest = deferred<Blob>();
    let reportSignal: AbortSignal | undefined;
    getValidationRunReportMock.mockImplementation((_runId, _format, signal) => {
      reportSignal = signal;
      return reportRequest.promise;
    });

    const rendered = render(<ValidationRunReportExports runId={validationRunFixture.id} />);
    fireEvent.click(screen.getByRole('button', { name: 'Download JSON report' }));
    await waitFor(() => expect(reportSignal).toBeInstanceOf(AbortSignal));

    rendered.unmount();
    expect(reportSignal?.aborted).toBe(true);

    await act(async () => reportRequest.resolve(new Blob(['late report'])));
    expect(saveValidationRunReportMock).not.toHaveBeenCalled();
  });
});
