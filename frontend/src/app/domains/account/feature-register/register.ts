import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { email, form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthClient } from '@app/shared/auth/data/auth-client';
import { passwordPolicy } from '@app/shared/auth/util/password-policy';

interface RegisterFormModel {
  email: string;
  password: string;
  confirmPassword: string;
}

/** True when a 400 VALIDATION_FAILED problem names the password field. */
function passwordRejected(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) return false;
  const body = error.error as { errors?: Record<string, string> } | null;
  return !!body?.errors && 'password' in body.errors;
}

const registerSchema = schema<RegisterFormModel>((path) => {
  required(path.email);
  email(path.email);
  maxLength(path.email, 320);
  required(path.password);
  passwordPolicy(path.password);
  required(path.confirmPassword);
});

@Component({
  selector: 'app-register',
  imports: [FormField, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthClient);

  protected readonly model = signal<RegisterFormModel>({ email: '', password: '', confirmPassword: '' });
  protected readonly form = form(this.model, registerSchema);
  protected readonly error = signal<string | null>(null);
  protected readonly passwordMismatch = signal(false);
  protected readonly registered = signal(false);

  protected async signUp(): Promise<void> {
    this.error.set(null);
    this.passwordMismatch.set(false);

    const { email, password, confirmPassword } = this.model();
    if (password !== confirmPassword) {
      this.passwordMismatch.set(true);
      return;
    }

    await submit(this.form, async () => {
      try {
        await firstValueFrom(this.auth.register({ email, password }));
        this.registered.set(true);
      } catch (error) {
        this.error.set(this.messageFor(error));
      }
    });
  }

  private messageFor(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 400:
        // Length and letter/digit are checked here already; what the backend can still reject
        // is a well-known or trivially repetitive password.
        if (passwordRejected(error)) {
          return $localize`:@@register.passwordRejected:This password is too common or too simple. Please choose another one.`;
        }
        return $localize`:@@register.failed:Account creation failed. Please try again.`;
      case 409:
        return $localize`:@@register.emailInUse:An account with this e-mail address already exists.`;
      case 429:
        return $localize`:@@register.rateLimit:Too many attempts. Please wait a moment and try again.`;
      case 0:
        return $localize`:@@register.unreachable:The server is not reachable.`;
      default:
        return $localize`:@@register.failed:Account creation failed. Please try again.`;
    }
  }
}
