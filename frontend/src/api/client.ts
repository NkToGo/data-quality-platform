import {
  decodeDatasets,
  decodeProblemDetails,
  decodeValidationIssues,
  decodeValidationRun,
  decodeValidationRuns,
  type Dataset,
  type ValidationIssue,
  type ValidationReportFormat,
  type ValidationRun,
} from './contracts';

export type ApiErrorKind = 'http' | 'network' | 'invalid-response';

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;

  constructor(kind: ApiErrorKind, message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
  }
}

type Decoder<T> = (value: unknown) => T | null;

const INVALID_RESPONSE_MESSAGE = 'The Data Quality API returned an unexpected response.';
const NETWORK_ERROR_MESSAGE = 'The Data Quality API could not be reached.';

const REPORT_MEDIA_TYPES: Record<ValidationReportFormat, string> = {
  json: 'application/json',
  csv: 'text/csv',
};

async function parseJson(response: Response): Promise<unknown> {
  const body = await response.text();
  if (body.trim().length === 0) {
    return null;
  }

  try {
    return JSON.parse(body) as unknown;
  } catch {
    return null;
  }
}

function httpError(response: Response, body: unknown): ApiError {
  const contentType = responseMediaType(response);
  const problemDetails =
    contentType === 'application/problem+json' ? decodeProblemDetails(body) : null;
  const message =
    problemDetails?.detail?.trim() ||
    problemDetails?.title?.trim() ||
    `Request failed with status ${response.status}.`;

  return new ApiError('http', message, response.status);
}

function responseMediaType(response: Response): string | undefined {
  return response.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function rethrowRequestError(error: unknown): never {
  if (error instanceof ApiError || isAbortError(error)) {
    throw error;
  }

  throw new ApiError('network', NETWORK_ERROR_MESSAGE);
}

async function getJson<T>(url: string, decoder: Decoder<T>, signal?: AbortSignal): Promise<T> {
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    });
    const body = await parseJson(response);

    if (!response.ok) {
      throw httpError(response, body);
    }

    const decoded = decoder(body);
    if (decoded === null) {
      throw new ApiError('invalid-response', INVALID_RESPONSE_MESSAGE);
    }

    return decoded;
  } catch (error) {
    rethrowRequestError(error);
  }
}

export function getDatasets(signal?: AbortSignal): Promise<Dataset[]> {
  return getJson('/api/datasets', decodeDatasets, signal);
}

export function getValidationRuns(signal?: AbortSignal): Promise<ValidationRun[]> {
  return getJson('/api/validation-runs', decodeValidationRuns, signal);
}

export function getValidationRun(runId: string, signal?: AbortSignal): Promise<ValidationRun> {
  return getJson(`/api/validation-runs/${encodeURIComponent(runId)}`, decodeValidationRun, signal);
}

export function getValidationIssues(
  runId: string,
  signal?: AbortSignal,
): Promise<ValidationIssue[]> {
  return getJson(
    `/api/validation-runs/${encodeURIComponent(runId)}/issues`,
    decodeValidationIssues,
    signal,
  );
}

export async function getValidationRunReport(
  runId: string,
  format: ValidationReportFormat,
  signal?: AbortSignal,
): Promise<Blob> {
  try {
    const response = await fetch(
      `/api/validation-runs/${encodeURIComponent(runId)}/report?format=${format}`,
      {
        method: 'GET',
        headers: { Accept: REPORT_MEDIA_TYPES[format] },
        signal,
      },
    );

    if (!response.ok) {
      throw httpError(response, await parseJson(response));
    }

    if (responseMediaType(response) !== REPORT_MEDIA_TYPES[format]) {
      throw new ApiError('invalid-response', INVALID_RESPONSE_MESSAGE);
    }

    return await response.blob();
  } catch (error) {
    rethrowRequestError(error);
  }
}
