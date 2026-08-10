import {
  decodeDatasets,
  decodeProblemDetails,
  decodeValidationIssues,
  decodeValidationRun,
  decodeValidationRuns,
  type Dataset,
  type ValidationIssue,
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
  const contentType = response.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase();
  const problemDetails =
    contentType === 'application/problem+json' ? decodeProblemDetails(body) : null;
  const message =
    problemDetails?.detail?.trim() ||
    problemDetails?.title?.trim() ||
    `Request failed with status ${response.status}.`;

  return new ApiError('http', message, response.status);
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
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
    if (error instanceof ApiError || isAbortError(error)) {
      throw error;
    }

    throw new ApiError('network', NETWORK_ERROR_MESSAGE);
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
