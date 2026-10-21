import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthClient } from './auth-client';
import {
  isTokenPair,
  LoginCredentials,
  LoginOutcome,
  TokenPair,
  User,
} from '@app/shared/auth/models/auth';

/**
 * Session state. The access token is kept in memory only (never in web storage); a page
 * reload restores the session through the refresh cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly client = inject(AuthClient);

  private readonly _user = signal<User | null>(null);
  private readonly _accessToken = signal<string | null>(null);
  private refreshing: Promise<boolean> | null = null;

  readonly user = this._user.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);

  /** Called once at startup: silently resumes a session if a valid refresh cookie exists. */
  async restoreSession(): Promise<void> {
    await this.refresh();
  }

  async login(credentials: LoginCredentials): Promise<LoginOutcome> {
    const response = await firstValueFrom(this.client.login(credentials));
    if (!isTokenPair(response)) return 'two-factor-required';
    await this.startSession(response);
    return 'success';
  }

  /** Single-flight: concurrent callers share one refresh request (the token rotates on use). */
  refresh(): Promise<boolean> {
    this.refreshing ??= this.doRefresh().finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.client.logout());
    } catch {
      // The local session ends regardless of whether the server call succeeded.
    }
    this.clear();
  }

  private async doRefresh(): Promise<boolean> {
    try {
      await this.startSession(await firstValueFrom(this.client.refresh()));
      return true;
    } catch {
      this.clear();
      return false;
    }
  }

  private async startSession(tokens: TokenPair): Promise<void> {
    this._accessToken.set(tokens.accessToken);
    this._user.set(await firstValueFrom(this.client.me()));
  }

  private clear(): void {
    this._accessToken.set(null);
    this._user.set(null);
  }
}
