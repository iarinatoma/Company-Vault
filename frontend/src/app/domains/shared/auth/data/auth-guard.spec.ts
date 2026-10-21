import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTestBackend } from '@app-testing/test-backend';
import { authGuard, guestGuard } from './auth-guard';
import { AuthStore } from './auth-store';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('auth guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/home' } as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideTestBackend()] });
  });

  async function logIn(): Promise<void> {
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(AuthStore).login({ email: 'a@b.c', password: 'x' });
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresInSeconds: 1 });
    await tick();
    http.expectOne('/api/v1/users/me').flush({
      id: '1',
      email: 'a@b.c',
      role: 'USER',
      twoFactorEnabled: false,
      createdAt: '2026-08-12T00:00:00Z',
    });
    await result;
  }

  it('redirects anonymous users to the login page and remembers the target', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(route, state));
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/login?returnUrl=%2Fhome');
  });

  it('lets logged-in users through', async () => {
    await logIn();
    expect(TestBed.runInInjectionContext(() => authGuard(route, state))).toBe(true);
  });

  it('lets anonymous users reach the login page', () => {
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state))).toBe(true);
  });

  it('sends logged-in users away from the login page', async () => {
    await logIn();
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state))).toBeInstanceOf(UrlTree);
  });
});
