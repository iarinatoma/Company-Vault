import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { email, form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthClient } from '@app/shared/auth/data/auth-client';

interface ForgotPasswordFormModel {
  email: string;
}

const forgotPasswordSchema = schema<ForgotPasswordFormModel>((path) => {
  required(path.email);
  email(path.email);
  maxLength(path.email, 320);
});

/**
 * Shows the same confirmation whether or not the e-mail has an account — the backend answers
 * identically, and the page must not leak the difference either.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [FormField, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  private readonly auth = inject(AuthClient);

  protected readonly model = signal<ForgotPasswordFormModel>({ email: '' });
  protected readonly form = form(this.model, forgotPasswordSchema);
  protected readonly error = signal<string | null>(null);
  protected readonly sent = signal(false);

  protected async requestLink(): Promise<void> {
    this.error.set(null);
    await submit(this.form, async () => {
      try {
        await firstValueFrom(this.auth.forgotPassword({ email: this.model().email }));
        this.sent.set(true);
      } catch (error) {
        this.error.set(this.messageFor(error));
      }
    });
  }

  private messageFor(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 429:
        return $localize`:@@forgotPassword.rateLimit:Too many attempts. Please wait a moment and try again.`;
      case 0:
        return $localize`:@@forgotPassword.unreachable:The server is not reachable.`;
      default:
        return $localize`:@@forgotPassword.failed:The request failed. Please try again.`;
    }
  }
}
