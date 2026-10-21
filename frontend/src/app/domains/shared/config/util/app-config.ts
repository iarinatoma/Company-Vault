import { InjectionToken } from '@angular/core';

export interface AppConfig {
  /** Empty in development: `proxy.conf.json` forwards `/api` to the backend. */
  readonly baseUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG', {
  providedIn: 'root',
  factory: () => ({ baseUrl: '' }),
});
