import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { email, form, FormField, required, schema, submit } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { AuthStore } from '@app/shared/auth/data/auth-store';
import { LoginCredentials } from '@app/shared/auth/models/auth';

const loginSchema = schema<LoginCredentials>((path) => {
  required(path.email);
  email(path.email);
  required(path.password);
});

function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}

@Component({
  selector: 'app-login',
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly model = signal<LoginCredentials>({ email: '', password: '' });
  protected readonly form = form(this.model, loginSchema);
  protected readonly error = signal<string | null>(null);

  protected async signIn(): Promise<void> {
    this.error.set(null);
    await submit(this.form, async () => {
      try {
        const outcome = await this.auth.login(this.model());
        if (outcome === 'two-factor-required') {
          this.error.set($localize`:@@login.twoFactor:Two-factor authentication is not supported by this client yet.`);
          return;
        }
        await this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
      } catch (e) {
        this.error.set(this.messageFor(e));
      }
    });
  }

  private messageFor(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 401:
        return $localize`:@@login.invalid:E-mail or password is incorrect.`;
      case 429:
        return $localize`:@@login.rateLimit:Too many attempts. Please wait a moment and try again.`;
      case 0:
        return $localize`:@@login.unreachable:The server is not reachable.`;
      default:
        return $localize`:@@login.failed:Sign-in failed. Please try again.`;
    }
  }
}
