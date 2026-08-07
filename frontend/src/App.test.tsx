import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { getDatasets, getValidationRuns } from './api/client';
import { datasetFixture, validationRunFixture } from './test/fixtures';
import { renderWithRouter } from './test/renderWithRouter';

vi.mock('./api/client', () => ({
  getDatasets: vi.fn(),
  getValidationRuns: vi.fn(),
}));

const getDatasetsMock = vi.mocked(getDatasets);
const getValidationRunsMock = vi.mocked(getValidationRuns);

describe('App routing', () => {
  beforeEach(() => {
    getDatasetsMock.mockReset().mockResolvedValue([]);
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

  it('renders an addressable Validation Run route', () => {
    renderWithRouter(<App />, `/runs/${validationRunFixture.id}`);

    expect(screen.getByRole('heading', { level: 2, name: 'Validation Run' })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.id)).toBeInTheDocument();
  });

  it('navigates from a Run link to the addressable placeholder route', async () => {
    getDatasetsMock.mockResolvedValue([datasetFixture]);
    getValidationRunsMock.mockResolvedValue([validationRunFixture]);

    renderWithRouter(<App />);

    fireEvent.click(await screen.findByRole('link', { name: validationRunFixture.id }));

    expect(screen.getByRole('heading', { level: 2, name: 'Validation Run' })).toBeInTheDocument();
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
