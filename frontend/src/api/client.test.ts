import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError,
  getDatasets,
  getValidationIssues,
  getValidationRun,
  getValidationRuns,
} from './client';
import {
  datasetFixture,
  problemDetailsFixture,
  validationIssueFixture,
  validationRunFixture,
} from '../test/fixtures';
import type { ValidationIssueSeverity, ValidationRuleType, ValidationRunStatus } from './contracts';

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(body: unknown, status = 200, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': contentType },
  });
}

function validationRunForStatus(status: ValidationRunStatus) {
  switch (status) {
    case 'PENDING':
      return {
        ...validationRunFixture,
        status,
        totalRows: 0,
        validRows: 0,
        invalidRows: 0,
        issueCount: 0,
        startedAt: null,
        finishedAt: null,
        failureReason: null,
      };
    case 'PROCESSING':
      return {
        ...validationRunFixture,
        status,
        validRows: 0,
        invalidRows: 0,
        issueCount: 0,
        finishedAt: null,
        failureReason: null,
      };
    case 'COMPLETED':
      return { ...validationRunFixture, status };
    case 'FAILED':
      return {
        ...validationRunFixture,
        status,
        totalRows: 0,
        validRows: 0,
        invalidRows: 0,
        issueCount: 0,
        failureReason: 'CSV content is malformed.',
      };
  }
}

describe('Data Quality API client', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  it('loads and validates Datasets through the relative API URL', async () => {
    const controller = new AbortController();
    fetchMock.mockResolvedValueOnce(jsonResponse([{ ...datasetFixture, ignored: 'value' }]));

    await expect(getDatasets(controller.signal)).resolves.toEqual([datasetFixture]);
    expect(fetchMock).toHaveBeenCalledWith('/api/datasets', {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
  });

  it('accepts a null Dataset description', async () => {
    const datasetWithoutDescription = { ...datasetFixture, description: null };
    fetchMock.mockResolvedValueOnce(jsonResponse([datasetWithoutDescription]));

    await expect(getDatasets()).resolves.toEqual([datasetWithoutDescription]);
  });

  it('loads and validates the Validation Run collection', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([validationRunFixture]));

    await expect(getValidationRuns()).resolves.toEqual([validationRunFixture]);
    expect(fetchMock).toHaveBeenCalledWith('/api/validation-runs', expect.any(Object));
  });

  it('loads one Validation Run with its nullable lifecycle fields', async () => {
    const pendingRun = {
      ...validationRunFixture,
      status: 'PENDING',
      totalRows: 0,
      validRows: 0,
      invalidRows: 0,
      issueCount: 0,
      startedAt: null,
      finishedAt: null,
      failureReason: null,
    };
    fetchMock.mockResolvedValueOnce(jsonResponse(pendingRun));

    await expect(getValidationRun(validationRunFixture.id)).resolves.toEqual(pendingRun);
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/validation-runs/${validationRunFixture.id}`,
      expect.any(Object),
    );
  });

  it.each<ValidationRunStatus>(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'])(
    'accepts the %s Validation Run status',
    async (status) => {
      const run = validationRunForStatus(status);
      fetchMock.mockResolvedValueOnce(jsonResponse(run));

      await expect(getValidationRun(validationRunFixture.id)).resolves.toEqual(run);
    },
  );

  it('rejects an unknown Validation Run status', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ ...validationRunFixture, status: 'UNKNOWN_STATUS' }),
    );

    await expect(getValidationRun(validationRunFixture.id)).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });

  it('loads and validates Validation Issues', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([validationIssueFixture]));

    await expect(getValidationIssues(validationRunFixture.id)).resolves.toEqual([
      validationIssueFixture,
    ]);
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/validation-runs/${validationRunFixture.id}/issues`,
      expect.any(Object),
    );
  });

  it.each<ValidationRuleType>([
    'REQUIRED_FIELD',
    'DATA_TYPE',
    'UNIQUENESS',
    'NUMERIC_RANGE',
    'DATE_FORMAT',
  ])('accepts the %s Validation Rule type', async (ruleType) => {
    const issue = { ...validationIssueFixture, ruleType };
    fetchMock.mockResolvedValueOnce(jsonResponse([issue]));

    await expect(getValidationIssues(validationRunFixture.id)).resolves.toEqual([issue]);
  });

  it.each<ValidationIssueSeverity>(['ERROR', 'WARNING'])(
    'accepts the %s Validation Issue severity',
    async (severity) => {
      const issue = { ...validationIssueFixture, severity };
      fetchMock.mockResolvedValueOnce(jsonResponse([issue]));

      await expect(getValidationIssues(validationRunFixture.id)).resolves.toEqual([issue]);
    },
  );

  it('accepts a null Validation Issue observedValue', async () => {
    const issueWithoutObservedValue = { ...validationIssueFixture, observedValue: null };
    fetchMock.mockResolvedValueOnce(jsonResponse([issueWithoutObservedValue]));

    await expect(getValidationIssues(validationRunFixture.id)).resolves.toEqual([
      issueWithoutObservedValue,
    ]);
  });

  it.each([
    ['Validation Rule type', { ruleType: 'UNKNOWN_RULE' }],
    ['Validation Issue severity', { severity: 'INFO' }],
  ])('rejects an unknown %s', async (_label, override) => {
    fetchMock.mockResolvedValueOnce(jsonResponse([{ ...validationIssueFixture, ...override }]));

    await expect(getValidationIssues(validationRunFixture.id)).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });

  it('uses the RFC Problem Details detail for an HTTP error', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(problemDetailsFixture, 404, 'application/problem+json'),
    );

    await expect(getValidationRun(validationRunFixture.id)).rejects.toMatchObject({
      name: 'ApiError',
      kind: 'http',
      status: 404,
      message: problemDetailsFixture.detail,
    });
  });

  it('uses the Problem Details title when no detail is available', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ title: 'Request rejected', status: 400 }, 400, 'application/problem+json'),
    );

    await expect(getDatasets()).rejects.toMatchObject({
      kind: 'http',
      status: 400,
      message: 'Request rejected',
    });
  });

  it('does not trust Problem Details-like text from another media type', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ detail: 'Internal gateway implementation details' }, 502, 'application/json'),
    );

    await expect(getDatasets()).rejects.toMatchObject({
      kind: 'http',
      status: 502,
      message: 'Request failed with status 502.',
    });
  });

  it('uses a status-only fallback for a non-JSON HTTP error without exposing its body', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('<html>Internal implementation details</html>', {
        status: 500,
        headers: { 'Content-Type': 'text/html' },
      }),
    );

    const request = getDatasets();

    await expect(request).rejects.toMatchObject({
      kind: 'http',
      status: 500,
      message: 'Request failed with status 500.',
    });
    await expect(request).rejects.not.toThrow('Internal implementation details');
  });

  it('reports network failures with an application-owned message', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Connection refused at a private address'));

    await expect(getDatasets()).rejects.toEqual(
      new ApiError('network', 'The Data Quality API could not be reached.'),
    );
  });

  it('rejects malformed successful JSON responses', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('{not-json', {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(getDatasets()).rejects.toEqual(
      new ApiError('invalid-response', 'The Data Quality API returned an unexpected response.'),
    );
  });

  it('rejects successful responses with an unexpected contract shape', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([{ ...datasetFixture, id: 'not-a-uuid' }]));

    await expect(getDatasets()).rejects.toMatchObject({
      kind: 'invalid-response',
      message: 'The Data Quality API returned an unexpected response.',
    });
  });

  it('does not convert an aborted request into a network error', async () => {
    const abortError = new DOMException('The operation was aborted.', 'AbortError');
    fetchMock.mockRejectedValueOnce(abortError);

    await expect(getDatasets()).rejects.toBe(abortError);
  });
});
