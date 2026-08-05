const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const validationRunStatuses = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'] as const;
export type ValidationRunStatus = (typeof validationRunStatuses)[number];

export const validationRuleTypes = [
  'REQUIRED_FIELD',
  'DATA_TYPE',
  'UNIQUENESS',
  'NUMERIC_RANGE',
  'DATE_FORMAT',
] as const;
export type ValidationRuleType = (typeof validationRuleTypes)[number];

export const validationIssueSeverities = ['ERROR', 'WARNING'] as const;
export type ValidationIssueSeverity = (typeof validationIssueSeverities)[number];

export interface Dataset {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
}

export interface ValidationRun {
  id: string;
  datasetId: string;
  sourceFileId: string;
  profileId: string;
  status: ValidationRunStatus;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  issueCount: number;
  startedAt: string | null;
  finishedAt: string | null;
  failureReason: string | null;
}

export interface ValidationIssue {
  id: string;
  runId: string;
  rowNumber: number;
  fieldName: string;
  ruleType: ValidationRuleType;
  severity: ValidationIssueSeverity;
  message: string;
  observedValue: string | null;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

type JsonRecord = Record<string, unknown>;

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isNonnegativeSafeInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) >= 0;
}

function isOneOf<T extends readonly string[]>(value: unknown, choices: T): value is T[number] {
  return typeof value === 'string' && choices.includes(value);
}

function decodeArray<T>(value: unknown, decodeItem: (item: unknown) => T | null): T[] | null {
  if (!Array.isArray(value)) {
    return null;
  }

  const decoded: T[] = [];
  for (const item of value) {
    const decodedItem = decodeItem(item);
    if (decodedItem === null) {
      return null;
    }
    decoded.push(decodedItem);
  }

  return decoded;
}

export function decodeDataset(value: unknown): Dataset | null {
  if (
    !isRecord(value) ||
    !isUuid(value.id) ||
    typeof value.name !== 'string' ||
    !isNullableString(value.description) ||
    typeof value.createdAt !== 'string'
  ) {
    return null;
  }

  return {
    id: value.id,
    name: value.name,
    description: value.description,
    createdAt: value.createdAt,
  };
}

export function decodeDatasets(value: unknown): Dataset[] | null {
  return decodeArray(value, decodeDataset);
}

export function decodeValidationRun(value: unknown): ValidationRun | null {
  if (
    !isRecord(value) ||
    !isUuid(value.id) ||
    !isUuid(value.datasetId) ||
    !isUuid(value.sourceFileId) ||
    !isUuid(value.profileId) ||
    !isOneOf(value.status, validationRunStatuses) ||
    !isNonnegativeSafeInteger(value.totalRows) ||
    !isNonnegativeSafeInteger(value.validRows) ||
    !isNonnegativeSafeInteger(value.invalidRows) ||
    !isNonnegativeSafeInteger(value.issueCount) ||
    !isNullableString(value.startedAt) ||
    !isNullableString(value.finishedAt) ||
    !isNullableString(value.failureReason)
  ) {
    return null;
  }

  return {
    id: value.id,
    datasetId: value.datasetId,
    sourceFileId: value.sourceFileId,
    profileId: value.profileId,
    status: value.status,
    totalRows: value.totalRows,
    validRows: value.validRows,
    invalidRows: value.invalidRows,
    issueCount: value.issueCount,
    startedAt: value.startedAt,
    finishedAt: value.finishedAt,
    failureReason: value.failureReason,
  };
}

export function decodeValidationRuns(value: unknown): ValidationRun[] | null {
  return decodeArray(value, decodeValidationRun);
}

export function decodeValidationIssue(value: unknown): ValidationIssue | null {
  if (
    !isRecord(value) ||
    !isUuid(value.id) ||
    !isUuid(value.runId) ||
    !isNonnegativeSafeInteger(value.rowNumber) ||
    value.rowNumber < 2 ||
    typeof value.fieldName !== 'string' ||
    !isOneOf(value.ruleType, validationRuleTypes) ||
    !isOneOf(value.severity, validationIssueSeverities) ||
    typeof value.message !== 'string' ||
    !isNullableString(value.observedValue)
  ) {
    return null;
  }

  return {
    id: value.id,
    runId: value.runId,
    rowNumber: value.rowNumber,
    fieldName: value.fieldName,
    ruleType: value.ruleType,
    severity: value.severity,
    message: value.message,
    observedValue: value.observedValue,
  };
}

export function decodeValidationIssues(value: unknown): ValidationIssue[] | null {
  return decodeArray(value, decodeValidationIssue);
}

export function decodeProblemDetails(value: unknown): ProblemDetails | null {
  if (!isRecord(value)) {
    return null;
  }

  const type = typeof value.type === 'string' ? value.type : undefined;
  const title = typeof value.title === 'string' ? value.title : undefined;
  const status = isNonnegativeSafeInteger(value.status) ? value.status : undefined;
  const detail = typeof value.detail === 'string' ? value.detail : undefined;
  const instance = typeof value.instance === 'string' ? value.instance : undefined;

  if (
    type === undefined &&
    title === undefined &&
    status === undefined &&
    detail === undefined &&
    instance === undefined
  ) {
    return null;
  }

  return { type, title, status, detail, instance };
}
