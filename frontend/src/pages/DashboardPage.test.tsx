import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDatasets, getValidationRuns } from '../api/client';
import type { Dataset, ValidationRun } from '../api/contracts';
import { datasetFixture, validationRunFixture } from '../test/fixtures';
import { renderWithRouter } from '../test/renderWithRouter';
import { DashboardPage } from './DashboardPage';

vi.mock('../api/client', () => ({
  getDatasets: vi.fn(),
  getValidationRuns: vi.fn(),
}));

const getDatasetsMock = vi.mocked(getDatasets);
const getValidationRunsMock = vi.mocked(getValidationRuns);

const secondDataset: Dataset = {
  ...datasetFixture,
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  name: 'Orders import',
  description: null,
  createdAt: '2026-07-19T08:15:00Z',
};

const secondRun: ValidationRun = {
  ...validationRunFixture,
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  datasetId: secondDataset.id,
  status: 'PENDING',
  totalRows: 0,
  validRows: 0,
  invalidRows: 0,
  issueCount: 0,
  startedAt: null,
  finishedAt: null,
};

const completedRun: ValidationRun = {
  ...validationRunFixture,
  totalRows: 17,
  validRows: 12,
  invalidRows: 5,
  issueCount: 8,
};

function deferred<T>() {
  let resolvePromise: (value: T) => void = () => undefined;
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve;
  });

  return { promise, resolve: resolvePromise };
}

function bodyRows(tableName: string) {
  return within(screen.getByRole('table', { name: tableName }))
    .getAllByRole('row')
    .slice(1);
}

describe('DashboardPage', () => {
  beforeEach(() => {
    getDatasetsMock.mockReset().mockResolvedValue([datasetFixture]);
    getValidationRunsMock.mockReset().mockResolvedValue([validationRunFixture]);
  });

  it('renders both collections in backend order and resolves Dataset names', async () => {
    getDatasetsMock.mockResolvedValue([secondDataset, datasetFixture]);
    getValidationRunsMock.mockResolvedValue([secondRun, completedRun]);

    renderWithRouter(<DashboardPage />);

    await screen.findByRole('table', { name: 'Datasets' });
    await screen.findByRole('table', { name: 'Validation Runs' });

    const datasetRows = bodyRows('Datasets');
    expect(datasetRows).toHaveLength(2);
    expect(within(datasetRows[0]).getByText(secondDataset.name)).toBeInTheDocument();
    expect(within(datasetRows[1]).getByText(datasetFixture.name)).toBeInTheDocument();
    expect(within(datasetRows[0]).getByText('No description')).toBeInTheDocument();
    expect(within(datasetRows[0]).getByText(secondDataset.id)).toBeInTheDocument();
    expect(within(datasetRows[0]).getByText(secondDataset.createdAt)).toBeInTheDocument();
    expect(within(datasetRows[1]).getByText(datasetFixture.description!)).toBeInTheDocument();

    const runRows = bodyRows('Validation Runs');
    expect(runRows).toHaveLength(2);
    expect(within(runRows[0]).getByRole('link', { name: secondRun.id })).toBeInTheDocument();
    expect(within(runRows[1]).getByRole('link', { name: completedRun.id })).toBeInTheDocument();
    expect(within(runRows[0]).getByText(secondDataset.name)).toBeInTheDocument();
    expect(within(runRows[1]).getByText(datasetFixture.name)).toBeInTheDocument();
    expect(within(runRows[0]).getByText('Pending')).toBeInTheDocument();
    expect(within(runRows[0]).getAllByText('0')).toHaveLength(4);
    expect(within(runRows[1]).getByText('Completed')).toBeInTheDocument();
    expect(within(runRows[1]).getByText('17')).toBeInTheDocument();
    expect(within(runRows[1]).getByText('12')).toBeInTheDocument();
    expect(within(runRows[1]).getByText('5')).toBeInTheDocument();
    expect(within(runRows[1]).getByText('8')).toBeInTheDocument();

    expect(screen.queryByText(/recent/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/background|actively processing/i)).not.toBeInTheDocument();
  });

  it('falls back to the Dataset UUID when no matching Dataset is loaded', async () => {
    const unknownDatasetId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
    getValidationRunsMock.mockResolvedValue([
      { ...validationRunFixture, datasetId: unknownDatasetId },
    ]);

    renderWithRouter(<DashboardPage />);

    const runTable = await screen.findByRole('table', { name: 'Validation Runs' });
    expect(within(runTable).getByText(unknownDatasetId)).toBeInTheDocument();
    expect(within(runTable).queryByText(datasetFixture.name)).not.toBeInTheDocument();
  });

  it('keeps Runs visible with UUID fallback when Datasets fail and retries only Datasets', async () => {
    getDatasetsMock
      .mockRejectedValueOnce(new Error('Dataset service is unavailable.'))
      .mockResolvedValueOnce([datasetFixture]);

    renderWithRouter(<DashboardPage />);

    expect(await screen.findByText('Datasets could not be loaded')).toBeInTheDocument();
    const runTable = await screen.findByRole('table', { name: 'Validation Runs' });
    expect(within(runTable).getByText(validationRunFixture.datasetId)).toBeInTheDocument();
    expect(within(runTable).queryByText(datasetFixture.name)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Retry Datasets' }));

    expect(await within(runTable).findByText(datasetFixture.name)).toBeInTheDocument();
    expect(getDatasetsMock).toHaveBeenCalledTimes(2);
    expect(getValidationRunsMock).toHaveBeenCalledOnce();
  });

  it('keeps Datasets visible when Runs fail and retries only Runs', async () => {
    getValidationRunsMock
      .mockRejectedValueOnce(new Error('Validation Run service is unavailable.'))
      .mockResolvedValueOnce([validationRunFixture]);

    renderWithRouter(<DashboardPage />);

    const datasetTable = await screen.findByRole('table', { name: 'Datasets' });
    expect(within(datasetTable).getByText(datasetFixture.name)).toBeInTheDocument();
    expect(await screen.findByText('Validation Runs could not be loaded')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Retry Validation Runs' }));

    expect(await screen.findByRole('link', { name: validationRunFixture.id })).toBeInTheDocument();
    expect(getValidationRunsMock).toHaveBeenCalledTimes(2);
    expect(getDatasetsMock).toHaveBeenCalledOnce();
  });

  it('shows Runs while the Dataset request is still loading', async () => {
    const datasetsRequest = deferred<Dataset[]>();
    getDatasetsMock.mockImplementation(() => datasetsRequest.promise);

    renderWithRouter(<DashboardPage />);

    expect(await screen.findByText('Loading Datasets…')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: validationRunFixture.id })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.datasetId)).toBeInTheDocument();
  });

  it('shows Datasets while the Validation Run request is still loading', async () => {
    const runsRequest = deferred<ValidationRun[]>();
    getValidationRunsMock.mockImplementation(() => runsRequest.promise);

    renderWithRouter(<DashboardPage />);

    expect(await screen.findByText(datasetFixture.name)).toBeInTheDocument();
    expect(screen.getByText('Loading Validation Runs…')).toBeInTheDocument();
  });

  it('shows the Dataset empty state without hiding Validation Runs', async () => {
    getDatasetsMock.mockResolvedValue([]);

    renderWithRouter(<DashboardPage />);

    expect(await screen.findByText('No Datasets')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: validationRunFixture.id })).toBeInTheDocument();
  });

  it('shows the Validation Run empty state without hiding Datasets', async () => {
    getValidationRunsMock.mockResolvedValue([]);

    renderWithRouter(<DashboardPage />);

    expect(await screen.findByText('No Validation Runs')).toBeInTheDocument();
    expect(await screen.findByText(datasetFixture.name)).toBeInTheDocument();
  });

  it('passes independent AbortSignals to the two API requests', async () => {
    renderWithRouter(<DashboardPage />);

    await waitFor(() => {
      expect(getDatasetsMock).toHaveBeenCalledOnce();
      expect(getValidationRunsMock).toHaveBeenCalledOnce();
    });

    expect(getDatasetsMock.mock.calls[0]?.[0]).toBeInstanceOf(AbortSignal);
    expect(getValidationRunsMock.mock.calls[0]?.[0]).toBeInstanceOf(AbortSignal);
    expect(getDatasetsMock.mock.calls[0]?.[0]).not.toBe(getValidationRunsMock.mock.calls[0]?.[0]);
  });
});
