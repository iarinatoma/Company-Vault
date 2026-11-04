import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { AuthClient } from './auth-client';

describe('AuthClient', () => {
  let authClient: AuthClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    authClient = TestBed.inject(AuthClient);
    http = TestBed.inject(HttpTestingController);
  });


  afterEach(() => http.verify());

  it('registers a new account through the public auth endpoint', async () => {
    const registration = firstValueFrom(
      authClient.register({ email: 'new@example.com', password: 'a-long-password' }),
    );
    const request = http.expectOne('/api/v1/auth/register');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'new@example.com', password: 'a-long-password' });
    request.flush({
      id: '1',
      email: 'new@example.com',
      role: 'USER',
      twoFactorEnabled: false,
      createdAt: '2026-11-03T00:00:00Z',
    });

    expect(await registration).toEqual({
      id: '1',
      email: 'new@example.com',
      role: 'USER',
      twoFactorEnabled: false,
      createdAt: '2026-11-03T00:00:00Z',
    });
  });
  it('requests a password reset link for an e-mail address', async () => {
    const done = firstValueFrom(authClient.forgotPassword({ email: 'someone@example.com' }));
    const request = http.expectOne('/api/v1/auth/forgot-password');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'someone@example.com' });
    request.flush(null, { status: 202, statusText: 'Accepted' });

    await done;
  });

  it('sets a new password with the token from the reset link', async () => {
    const done = firstValueFrom(
      authClient.resetPassword({ token: 'reset-token', newPassword: 'a-brand-new-password' }),
    );
    const request = http.expectOne('/api/v1/auth/reset-password');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'reset-token', newPassword: 'a-brand-new-password' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    await done;
  });
});
