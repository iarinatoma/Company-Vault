import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { email, form, FormField, maxLength, minLength, required, schema, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthClient } from '@app/shared/auth/data/auth-client';

interface RegisterFormModel {
  email: string;
  password: string;
  confirmPassword: string;
}

const registerSchema = schema<RegisterFormModel>((path) => {
  required(path.email);
  email(path.email);
  maxLength(path.email, 320);
  required(path.password);
  minLength(path.password, 12);
  maxLength(path.password, 128);
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
