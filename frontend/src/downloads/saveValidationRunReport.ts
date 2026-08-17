import type { ValidationReportFormat } from '../api/contracts';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function reportFileName(runId: string, format: ValidationReportFormat): string {
  const safeRunId = UUID_PATTERN.test(runId) ? `${runId.toLowerCase()}-` : '';
  return `validation-run-${safeRunId}report.${format}`;
}

export function saveValidationRunReport(
  blob: Blob,
  runId: string,
  format: ValidationReportFormat,
): string {
  const fileName = reportFileName(runId, format);
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');

  link.href = objectUrl;
  link.download = fileName;
  link.hidden = true;
  document.body.append(link);

  try {
    link.click();
  } catch (error) {
    URL.revokeObjectURL(objectUrl);
    throw error;
  } finally {
    link.remove();
  }

  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);

  return fileName;
}
