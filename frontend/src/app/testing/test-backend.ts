import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { APP_CONFIG } from '@app/shared/config/util/app-config';

export function provideTestBackend(): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: APP_CONFIG, useValue: { baseUrl: '' } },
  ];
}
