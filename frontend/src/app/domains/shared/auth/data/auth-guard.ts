import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth-store';

/** Protects routes: anonymous users are sent to the login page and return afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isLoggedIn()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** The login page itself is only for anonymous users. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isLoggedIn() ? router.createUrlTree(['/']) : true;
};
