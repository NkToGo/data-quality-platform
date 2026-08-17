import { useEffect, useRef, useState } from 'react';
import { getValidationRunReport } from '../api/client';
import type { ValidationReportFormat } from '../api/contracts';
import { saveValidationRunReport } from '../downloads/saveValidationRunReport';
import { ErrorState } from './AsyncState';

const FORMAT_LABELS: Record<ValidationReportFormat, string> = {
  json: 'JSON',
  csv: 'CSV',
};

type DownloadState =
  | { status: 'idle' }
  | { status: 'loading'; format: ValidationReportFormat }
  | { status: 'success'; format: ValidationReportFormat; fileName: string }
  | { status: 'error'; format: ValidationReportFormat; message: string };

interface ValidationRunReportExportsProps {
  runId: string;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.';
}

export function ValidationRunReportExports({ runId }: ValidationRunReportExportsProps) {
  const [download, setDownload] = useState<DownloadState>({ status: 'idle' });
  const activeRequest = useRef<AbortController | null>(null);
  const downloadButtons = useRef<Partial<Record<ValidationReportFormat, HTMLButtonElement | null>>>(
    {},
  );
  const restoreFocusTo = useRef<ValidationReportFormat | null>(null);

  useEffect(
    () => () => {
      activeRequest.current?.abort();
      activeRequest.current = null;
    },
    [],
  );

  useEffect(() => {
    if (download.status !== 'success' && download.status !== 'error') {
      return;
    }

    const format = restoreFocusTo.current;
    if (format === null || format !== download.format) {
      return;
    }

    downloadButtons.current[format]?.focus();
    restoreFocusTo.current = null;
  }, [download]);

  const startDownload = async (format: ValidationReportFormat, restoreFocus = false) => {
    if (activeRequest.current !== null) {
      return;
    }

    if (restoreFocus) {
      restoreFocusTo.current = format;
      downloadButtons.current[format]?.focus();
    }

    const controller = new AbortController();
    activeRequest.current = controller;
    setDownload({ status: 'loading', format });

    try {
      const blob = await getValidationRunReport(runId, format, controller.signal);
      if (controller.signal.aborted) {
        return;
      }

      const fileName = saveValidationRunReport(blob, runId, format);
      setDownload({ status: 'success', format, fileName });
    } catch (error) {
      if (!controller.signal.aborted) {
        setDownload({ status: 'error', format, message: errorMessage(error) });
      }
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
      }
    }
  };

  const downloading = download.status === 'loading';

  return (
    <section className="panel run-exports" aria-labelledby="run-exports-heading">
      <p className="eyebrow">Portable results</p>
      <h2 id="run-exports-heading">Export report</h2>
      <p>
        Download a read-only snapshot of this persisted Validation Run and its persisted Issues.
        Reports are available for Pending, Processing, Completed, and Failed Runs.
      </p>
      <p className="muted-text">
        Pending and Processing reports reflect the current persisted state and may be incomplete.
      </p>
      <p className="muted-text">
        Active Issue filters affect only the table below; exports always include the full persisted
        report.
      </p>

      <div className="report-actions" role="group" aria-label="Report download formats">
        {(['json', 'csv'] as const).map((format) => (
          <button
            key={format}
            type="button"
            className="secondary-action"
            disabled={downloading}
            aria-busy={download.status === 'loading' && download.format === format}
            ref={(button) => {
              downloadButtons.current[format] = button;
            }}
            onClick={() => void startDownload(format)}
          >
            Download {FORMAT_LABELS[format]} report
          </button>
        ))}
      </div>

      {download.status === 'loading' ? (
        <div className="report-download-status" role="status" aria-live="polite">
          <p>Preparing {FORMAT_LABELS[download.format]} report download…</p>
        </div>
      ) : null}

      {download.status === 'success' ? (
        <div className="report-download-status" role="status" aria-live="polite">
          <p>{download.fileName} download started.</p>
        </div>
      ) : null}

      {download.status === 'error' ? (
        <div className="report-download-error">
          <ErrorState
            title={`${FORMAT_LABELS[download.format]} report could not be downloaded`}
            message={download.message}
            retryLabel={`Retry ${FORMAT_LABELS[download.format]} report download`}
            onRetry={() => void startDownload(download.format, true)}
          />
        </div>
      ) : null}
    </section>
  );
}
