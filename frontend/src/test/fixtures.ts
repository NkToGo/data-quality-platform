import type { Dataset, ProblemDetails, ValidationIssue, ValidationRun } from '../api/contracts';

export const datasetFixture: Dataset = {
  id: '47d9bea4-1130-4b9b-8fb3-ea23893d51e5',
  name: 'Customer import',
  description: 'Customer data received for validation',
  createdAt: '2026-07-20T12:34:56.123456Z',
};

export const validationRunFixture: ValidationRun = {
  id: '1d97a9a7-eb56-44da-a566-a9630f23cbcb',
  datasetId: datasetFixture.id,
  sourceFileId: '54985ec5-103b-4d2b-95f3-0b57e2d74336',
  profileId: '6dc81327-2a6b-46c9-9a09-43a64f989ac2',
  status: 'COMPLETED',
  totalRows: 2,
  validRows: 1,
  invalidRows: 1,
  issueCount: 1,
  startedAt: '2026-07-26T12:34:56.123456Z',
  finishedAt: '2026-07-26T12:34:56.234567Z',
  failureReason: null,
};

export const validationIssueFixture: ValidationIssue = {
  id: '9f14aeba-fec4-476a-8e2d-216871f44b42',
  runId: validationRunFixture.id,
  rowNumber: 2,
  fieldName: 'email',
  ruleType: 'REQUIRED_FIELD',
  severity: 'ERROR',
  message: 'Value is required.',
  observedValue: '',
};

export const problemDetailsFixture: ProblemDetails = {
  title: 'Validation Run not found',
  status: 404,
  detail: `Validation Run '${validationRunFixture.id}' was not found.`,
  instance: `/api/validation-runs/${validationRunFixture.id}`,
};
