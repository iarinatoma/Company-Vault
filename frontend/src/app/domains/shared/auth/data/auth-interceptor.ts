import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { AuthStore } from './auth-store';

const withToken = (req: HttpRequest<unknown>, token: string) =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

/**
 * Adds the bearer token to API calls. On a 401 it refreshes the session once and retries;
 * if the refresh fails the user is logged out (the guard then redirects to the login page).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const apiPrefix = `${inject(APP_CONFIG).baseUrl}/api/`;

  if (!req.url.startsWith(apiPrefix) || req.url.startsWith(`${apiPrefix}v1/auth/`)) {
    return next(req);
  }

  const token = auth.accessToken();
  if (!token) return next(req);

  return next(withToken(req, token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return from(auth.refresh()).pipe(
        switchMap((refreshed) => {
          const fresh = auth.accessToken();
          return refreshed && fresh ? next(withToken(req, fresh)) : throwError(() => error);
        }),
      );
    }),
  );
};
