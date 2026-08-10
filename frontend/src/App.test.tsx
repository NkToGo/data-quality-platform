import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import {
  getDatasets,
  getValidationIssues,
  getValidationRun,
  getValidationRuns,
} from './api/client';
import { datasetFixture, validationRunFixture } from './test/fixtures';
import { renderWithRouter } from './test/renderWithRouter';

vi.mock('./api/client', () => ({
  getDatasets: vi.fn(),
  getValidationIssues: vi.fn(),
  getValidationRun: vi.fn(),
  getValidationRuns: vi.fn(),
}));

const getDatasetsMock = vi.mocked(getDatasets);
const getValidationIssuesMock = vi.mocked(getValidationIssues);
const getValidationRunMock = vi.mocked(getValidationRun);
const getValidationRunsMock = vi.mocked(getValidationRuns);

describe('App routing', () => {
  beforeEach(() => {
    getDatasetsMock.mockReset().mockResolvedValue([]);
    getValidationIssuesMock.mockReset().mockResolvedValue([]);
    getValidationRunMock.mockReset().mockResolvedValue(validationRunFixture);
    getValidationRunsMock.mockReset().mockResolvedValue([]);
  });

  it('renders the dashboard route', async () => {
    renderWithRouter(<App />);

    expect(
      screen.getByRole('heading', { level: 2, name: 'Data quality dashboard' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('No Datasets')).toBeInTheDocument();
    expect(await screen.findByText('No Validation Runs')).toBeInTheDocument();
  });

  it('renders an addressable Validation Run detail route', async () => {
    renderWithRouter(<App />, `/runs/${validationRunFixture.id}`);

    expect(await screen.findByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.id)).toBeInTheDocument();
    expect(getValidationRunMock).toHaveBeenCalledWith(
      validationRunFixture.id,
      expect.any(AbortSignal),
    );
  });

  it('navigates from a Run link to the addressable detail route', async () => {
    getDatasetsMock.mockResolvedValue([datasetFixture]);
    getValidationRunsMock.mockResolvedValue([validationRunFixture]);

    renderWithRouter(<App />);

    fireEvent.click(await screen.findByRole('link', { name: validationRunFixture.id }));

    expect(await screen.findByRole('heading', { name: 'Run summary' })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.id)).toBeInTheDocument();
  });

  it('renders an unmatched route and links back to the dashboard', async () => {
    renderWithRouter(<App />, '/does-not-exist');

    expect(screen.getByRole('heading', { level: 2, name: 'Page not found' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Return to dashboard' }));

    expect(
      screen.getByRole('heading', { level: 2, name: 'Data quality dashboard' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('No Datasets')).toBeInTheDocument();
  });
});
