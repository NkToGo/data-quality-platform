import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { validationRunFixture } from '../test/fixtures';
import { saveValidationRunReport } from './saveValidationRunReport';

describe('saveValidationRunReport', () => {
  let createObjectUrl: ReturnType<typeof vi.spyOn>;
  let revokeObjectUrl: ReturnType<typeof vi.spyOn>;
  let clickLink: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:validation-report');
    revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    clickLink = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('starts a download with a deterministic UUID-based filename and queues URL cleanup', () => {
    const blob = new Blob(['{"status":"COMPLETED"}'], { type: 'application/json' });

    const fileName = saveValidationRunReport(blob, validationRunFixture.id, 'json');
    const clickedLink = clickLink.mock.instances[0] as HTMLAnchorElement;

    expect(fileName).toBe(`validation-run-${validationRunFixture.id}-report.json`);
    expect(clickedLink).toMatchObject({
      download: fileName,
      href: 'blob:validation-report',
      hidden: true,
    });
    expect(clickedLink?.isConnected).toBe(false);
    expect(createObjectUrl).toHaveBeenCalledWith(blob);
    expect(revokeObjectUrl).not.toHaveBeenCalled();

    vi.runOnlyPendingTimers();

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:validation-report');
  });

  it('does not place an unsafe identifier in the filename', () => {
    const fileName = saveValidationRunReport(new Blob(['report']), '../../unsafe\0name', 'csv');
    const clickedLink = clickLink.mock.instances[0] as HTMLAnchorElement;

    expect(fileName).toBe('validation-run-report.csv');
    expect(clickedLink?.download).toBe(fileName);
  });

  it('cleans up the temporary link and object URL when clicking fails', () => {
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('Browser download failed.');
    });

    expect(() =>
      saveValidationRunReport(new Blob(['report']), validationRunFixture.id, 'json'),
    ).toThrow('Browser download failed.');
    expect(document.querySelector('a[download]')).toBeNull();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:validation-report');
  });
});
