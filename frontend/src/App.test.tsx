import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the foundation shell without claiming later features are available', () => {
    render(<App />);

    expect(
      screen.getByRole('heading', { level: 1, name: 'Data Quality Platform' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('These workflows are not available in Milestone 1.'),
    ).toBeInTheDocument();
  });
});
