import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';
import { validationRunFixture } from './test/fixtures';
import { renderWithRouter } from './test/renderWithRouter';

describe('App routing', () => {
  it('renders the dashboard route', () => {
    renderWithRouter(<App />);

    expect(
      screen.getByRole('heading', { level: 2, name: 'Dashboard foundation' }),
    ).toBeInTheDocument();
  });

  it('renders an addressable Validation Run route', () => {
    renderWithRouter(<App />, `/runs/${validationRunFixture.id}`);

    expect(screen.getByRole('heading', { level: 2, name: 'Validation Run' })).toBeInTheDocument();
    expect(screen.getByText(validationRunFixture.id)).toBeInTheDocument();
  });

  it('renders an unmatched route and links back to the dashboard', () => {
    renderWithRouter(<App />, '/does-not-exist');

    expect(screen.getByRole('heading', { level: 2, name: 'Page not found' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Return to dashboard' }));

    expect(
      screen.getByRole('heading', { level: 2, name: 'Dashboard foundation' }),
    ).toBeInTheDocument();
  });
});
