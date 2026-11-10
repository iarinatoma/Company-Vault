import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { form, FormField, required, schema, submit } from '@angular/forms/signals';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthClient } from '@app/shared/auth/data/auth-client';
import { passwordPolicy } from '@app/shared/auth/util/password-policy';

interface ResetPasswordFormModel {
  password: string;
  confirmPassword: string;
}

const resetPasswordSchema = schema<ResetPasswordFormModel>((path) => {
  required(path.password);
  passwordPolicy(path.password);
  required(path.confirmPassword);
});

/** The e-mailed link carries the token in the fragment (#token=...), which never reaches a server. */
function tokenFromFragment(fragment: string | null): string | null {
  const token = new URLSearchParams(fragment ?? '').get('token');
  return token && token.trim() ? token : null;
}

@Component({
  selector: 'app-reset-password',
  imports: [FormField, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reset-password.html',
})
export class ResetPassword {
  private readonly auth = inject(AuthClient);
  private readonly token = tokenFromFragment(inject(ActivatedRoute).snapshot.fragment);

  protected readonly hasToken = this.token !== null;
  protected readonly model = signal<ResetPasswordFormModel>({ password: '', confirmPassword: '' });
  protected readonly form = form(this.model, resetPasswordSchema);
  protected readonly error = signal<string | null>(null);
  protected readonly passwordMismatch = signal(false);
  protected readonly done = signal(false);

  constructor() {
    // The token now lives in memory only: drop it from the address bar and the history entry.
    inject(Location).replaceState('/reset-password');
  }

  protected async save(): Promise<void> {
    this.error.set(null);
    this.passwordMismatch.set(false);

    const token = this.token;
    const { password, confirmPassword } = this.model();
    if (!token) {
      return;
    }
    if (password !== confirmPassword) {
      this.passwordMismatch.set(true);
      return;
    }

    await submit(this.form, async () => {
      try {
        await firstValueFrom(this.auth.resetPassword({ token, newPassword: password }));
        this.done.set(true);
      } catch (error) {
        this.error.set(this.messageFor(error));
      }
    });
  }

  private messageFor(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 401:
      case 409:
        return $localize`:@@resetPassword.expired:This link is invalid, has expired or was already used. Request a new one.`;
      case 400:
        return $localize`:@@resetPassword.validation:The password does not meet the requirements.`;
      case 429:
        return $localize`:@@resetPassword.rateLimit:Too many attempts. Please wait a moment and try again.`;
      case 0:
        return $localize`:@@resetPassword.unreachable:The server is not reachable.`;
      default:
        return $localize`:@@resetPassword.failed:The password could not be changed. Please try again.`;
    }
  }
}
