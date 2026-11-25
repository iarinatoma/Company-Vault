import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';

/** How long a copied value may stay in the clipboard before it is emptied again. */
export const CLIPBOARD_CLEAR_MS = new InjectionToken<number>('CLIPBOARD_CLEAR_MS', {
  providedIn: 'root',
  factory: () => 30 * 1000,
});

/** How long the "cleared" or "failed" notice stays visible. */
const NOTICE_MS = 5 * 1000;

/** The part of the browser's Clipboard API that is needed; replaced by a fake in tests. */
export interface ClipboardWriter {
  writeText(text: string): Promise<void>;
}

export const CLIPBOARD_WRITER = new InjectionToken<ClipboardWriter>('CLIPBOARD_WRITER', {
  providedIn: 'root',
  factory: () => navigator.clipboard,
});

/**
 * Copies secret values to the clipboard and empties it again after a short time (spec 4.3:
 * "Kopieren mit automatischem Leeren der Zwischenablage"). Provided in root, so a pending clear
 * still happens after the user leaves the vault page.
 *
 * Reading the clipboard would need an extra browser permission, so the clear overwrites it
 * without checking what is in it. Browsers refuse clipboard writes while the tab is not focused;
 * then the clipboard is emptied as soon as the user comes back to the tab.
 */
@Injectable({ providedIn: 'root' })
export class SecureClipboard {
  private readonly writer = inject(CLIPBOARD_WRITER);
  private readonly clearAfterMs = inject(CLIPBOARD_CLEAR_MS);
  private readonly browserWindow = inject(DOCUMENT).defaultView;

  /** True while a copied value may still be in the clipboard. */
  private pending = false;
  private clearTimer: ReturnType<typeof setTimeout> | null = null;
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly clearOnFocus = () => void this.clear();

  private readonly _notice = signal<string | null>(null);
  /** Short feedback for the user: copied, cleared again, or copying failed. */
  readonly notice = this._notice.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.stopClearTimer();
      this.stopNoticeTimer();
      this.browserWindow?.removeEventListener('focus', this.clearOnFocus);
    });
  }

  /** Copies the text and schedules the clear; false if the browser refused. */
  async copy(text: string): Promise<boolean> {
    try {
      await this.writer.writeText(text);
    } catch {
      this.showNotice(
        $localize`:@@clipboard.failed:Copying failed. The browser did not allow access to the clipboard.`,
        NOTICE_MS,
      );
      return false;
    }
    this.pending = true;
    this.browserWindow?.removeEventListener('focus', this.clearOnFocus);
    this.stopClearTimer();
    this.clearTimer = setTimeout(() => void this.clear(), this.clearAfterMs);
    this.showNotice(
      $localize`:@@clipboard.copied:Copied. The clipboard will be emptied again automatically.`,
      null,
    );
    return true;
  }

  /** Empties the clipboard if a copied value may still be in it. */
  async clear(): Promise<void> {
    if (!this.pending) return;
    this.stopClearTimer();
    try {
      await this.writer.writeText('');
    } catch {
      this.browserWindow?.addEventListener('focus', this.clearOnFocus, { once: true });
      return;
    }
    this.pending = false;
    this.showNotice($localize`:@@clipboard.cleared:Clipboard emptied.`, NOTICE_MS);
  }

  private showNotice(text: string, hideAfterMs: number | null): void {
    this.stopNoticeTimer();
    this._notice.set(text);
    if (hideAfterMs !== null) {
      this.noticeTimer = setTimeout(() => this._notice.set(null), hideAfterMs);
    }
  }

  private stopClearTimer(): void {
    if (this.clearTimer !== null) {
      clearTimeout(this.clearTimer);
      this.clearTimer = null;
    }
  }

  private stopNoticeTimer(): void {
    if (this.noticeTimer !== null) {
      clearTimeout(this.noticeTimer);
      this.noticeTimer = null;
    }
  }
}
