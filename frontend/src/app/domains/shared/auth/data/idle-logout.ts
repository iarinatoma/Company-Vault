import { DOCUMENT } from '@angular/common';
import {
  DestroyRef,
  effect,
  inject,
  Injectable,
  InjectionToken,
  signal,
  untracked,
} from '@angular/core';
import { AuthStore } from './auth-store';

/** How long a signed-in user may be inactive before being signed out. */
export const IDLE_TIMEOUT_MS = new InjectionToken<number>('IDLE_TIMEOUT_MS', {
  providedIn: 'root',
  factory: () => 15 * 60 * 1000,
});

/** Anything that shows the user is still at the keyboard. */
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'scroll', 'touchstart'] as const;

/**
 * Signs the user out after a period without activity, so an unattended browser does not keep
 * a vault session open. Complements the server-side limits (15 min access token, absolute
 * session lifetime): those bound how long a token is valid, this bounds how long an idle tab
 * stays signed in.
 *
 * Started once at application startup (see `app.config.ts`); it watches activity only while
 * someone is signed in.
 */
@Injectable({ providedIn: 'root' })
export class IdleLogout {
  private readonly auth = inject(AuthStore);
  private readonly document = inject(DOCUMENT);
  private readonly timeoutMs = inject(IDLE_TIMEOUT_MS);

  private timer: ReturnType<typeof setTimeout> | null = null;
  private watching = false;
  private readonly onActivity = () => this.restartTimer();

  private readonly _signedOutForInactivity = signal(false);
  /** True after an automatic sign-out, so the login page can explain what happened. */
  readonly signedOutForInactivity = this._signedOutForInactivity.asReadonly();

  constructor() {
    effect(() => {
      const loggedIn = this.auth.isLoggedIn();
      untracked(() => (loggedIn ? this.watch() : this.unwatch()));
    });
    inject(DestroyRef).onDestroy(() => this.unwatch());
  }

  private watch(): void {
    if (this.watching) return;
    this.watching = true;
    this._signedOutForInactivity.set(false);
    for (const type of ACTIVITY_EVENTS) {
      this.document.addEventListener(type, this.onActivity, { passive: true, capture: true });
    }
    this.restartTimer();
  }

  private unwatch(): void {
    if (!this.watching) return;
    this.watching = false;
    for (const type of ACTIVITY_EVENTS) {
      this.document.removeEventListener(type, this.onActivity, { capture: true });
    }
    this.clearTimer();
  }

  private restartTimer(): void {
    this.clearTimer();
    this.timer = setTimeout(() => this.expire(), this.timeoutMs);
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private expire(): void {
    this.unwatch();
    this._signedOutForInactivity.set(true);
    // Ends the session on the server too; the shell then navigates to the login page.
    void this.auth.logout();
  }
}