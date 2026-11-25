import { TestBed } from '@angular/core/testing';
import { CLIPBOARD_CLEAR_MS, CLIPBOARD_WRITER, SecureClipboard } from './secure-clipboard';

const CLEAR_MS = 1000;

describe('SecureClipboard', () => {
  let writeText: ReturnType<typeof vi.fn>;
  let clipboard: SecureClipboard;

  beforeEach(() => {
    vi.useFakeTimers();
    writeText = vi.fn(async () => undefined);
    TestBed.configureTestingModule({
      providers: [
        { provide: CLIPBOARD_WRITER, useValue: { writeText } },
        { provide: CLIPBOARD_CLEAR_MS, useValue: CLEAR_MS },
      ],
    });
    clipboard = TestBed.inject(SecureClipboard);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('copies the text and empties the clipboard again after the timeout', async () => {
    expect(await clipboard.copy('s3cr3t')).toBe(true);
    expect(writeText).toHaveBeenLastCalledWith('s3cr3t');
    expect(clipboard.notice()).toContain('Copied');

    await vi.advanceTimersByTimeAsync(CLEAR_MS - 1);
    expect(writeText).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    expect(writeText).toHaveBeenLastCalledWith('');
    expect(clipboard.notice()).toBe('Clipboard emptied.');
  });

  it('restarts the timeout when something else is copied', async () => {
    await clipboard.copy('first');
    await vi.advanceTimersByTimeAsync(CLEAR_MS - 100);
    await clipboard.copy('second');
    await vi.advanceTimersByTimeAsync(CLEAR_MS - 100);
    expect(writeText).not.toHaveBeenCalledWith('');

    await vi.advanceTimersByTimeAsync(100);
    expect(writeText).toHaveBeenLastCalledWith('');
    expect(writeText).toHaveBeenCalledTimes(3);
  });

  it('reports a refused copy and schedules nothing', async () => {
    writeText.mockRejectedValueOnce(new Error('denied'));

    expect(await clipboard.copy('s3cr3t')).toBe(false);
    expect(clipboard.notice()).toContain('Copying failed');

    await vi.advanceTimersByTimeAsync(CLEAR_MS * 2);
    expect(writeText).toHaveBeenCalledTimes(1);
  });

  it('empties the clipboard as soon as the user comes back to an unfocused tab', async () => {
    await clipboard.copy('s3cr3t');
    writeText.mockRejectedValueOnce(new Error('Document is not focused.'));

    await vi.advanceTimersByTimeAsync(CLEAR_MS);
    expect(writeText).toHaveBeenCalledTimes(2);
    expect(clipboard.notice()).toContain('Copied');

    window.dispatchEvent(new Event('focus'));
    await vi.advanceTimersByTimeAsync(0);
    expect(writeText).toHaveBeenCalledTimes(3);
    expect(writeText).toHaveBeenLastCalledWith('');
    expect(clipboard.notice()).toBe('Clipboard emptied.');
  });
});
