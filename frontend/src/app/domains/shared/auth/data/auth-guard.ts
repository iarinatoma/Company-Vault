import { inject } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Role } from '@app/shared/auth/models/auth';
import { AuthStore } from './auth-store';

export const authGuard: CanActivateFn = (
  _route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
): boolean | UrlTree => {
  void _route;
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (authStore.isLoggedIn()) {
    return true;
  }

  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url },
  });
};

export const guestGuard: CanActivateFn = (): boolean | UrlTree => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (!authStore.isLoggedIn()) {
    return true;
  }

  return router.createUrlTree(['/home']);
};

export const roleGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
): boolean | UrlTree => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (!authStore.isLoggedIn()) {
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url },
    });
  }

  const allowedRoles = route.data['roles'] as Role[] | undefined;
  if (!allowedRoles || allowedRoles.length === 0 || authStore.hasRole(allowedRoles)) {
    return true;
  }

  return router.createUrlTree(['/home']);
};
