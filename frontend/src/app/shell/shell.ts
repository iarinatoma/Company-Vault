import { ChangeDetectionStrategy, Component, effect, inject, untracked } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '@app/shared/auth/data/auth-store';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
})
export class Shell {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  private wasLoggedIn = this.auth.isLoggedIn();

  constructor() {
    // Also covers a session that ends on its own (refresh failed): send the user to login.
    effect(() => {
      const loggedIn = this.auth.isLoggedIn();
      untracked(() => {
        if (this.wasLoggedIn && !loggedIn) void this.router.navigate(['/login']);
        this.wasLoggedIn = loggedIn;
      });
    });
  }

  protected logout(): void {
    void this.auth.logout();
  }
}
