import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { email, form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthClient } from '@app/shared/auth/data/auth-client';

interface RegisterFormModel {
  email: string;
}

const registerSchema = schema<RegisterFormModel>((path) => {
  required(path.email);
  email(path.email);
  maxLength(path.email, 320);
});

@Component({
  selector: 'app-register',
  imports: [FormField, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthClient);

  protected readonly model = signal<RegisterFormModel>({ email: '' });
  protected readonly form = form(this.model, registerSchema);
  protected readonly error = signal<string | null>(null);
  protected readonly registered = signal(false);

  protected async signUp(): Promise<void> {
    this.error.set(null);
    const { email } = this.model();

    await submit(this.form, async () => {
      try {
        await firstValueFrom(this.auth.register({ email }));
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
