import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withPreloading,
  PreloadAllModules,
} from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from '@app/shared/auth/data/auth-interceptor';
import { AuthStore } from '@app/shared/auth/data/auth-store';
import { IdleLogout } from '@app/shared/auth/data/idle-logout';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withPreloading(PreloadAllModules)),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Resume an existing session (refresh cookie) before the first route is activated.
    provideAppInitializer(() => inject(AuthStore).restoreSession()),
    // Sign out automatically after a period of inactivity (see IdleLogout).
    provideAppInitializer(() => {
      inject(IdleLogout);
    }),
  ],
};
