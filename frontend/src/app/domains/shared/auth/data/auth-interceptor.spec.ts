import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth-interceptor';
import { AuthStore } from './auth-store';

const tick = () => new Promise((resolve) => setTimeout(resolve));
const tokens = (n: number) => ({ accessToken: `access-${n}`, tokenType: 'Bearer', expiresInSeconds: 900 });
const me = { id: '1', email: 'a@b.c', role: 'USER', twoFactorEnabled: false, createdAt: '2026-08-12T00:00:00Z' };

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthStore;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);

    const login = auth.login({ email: 'a@b.c', password: 'x' });
    backend.expectOne('/api/v1/auth/login').flush(tokens(1));
    await tick();
    backend.expectOne('/api/v1/users/me').flush(me);
    await login;
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API calls', () => {
    http.get('/api/v1/things').subscribe();
    const req = backend.expectOne('/api/v1/things');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('does not add the token to other hosts', () => {
    http.get('https://elsewhere.example/data').subscribe();
    const req = backend.expectOne('https://elsewhere.example/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    let result: unknown;
    http.get('/api/v1/things').subscribe((value) => (result = value));

    backend.expectOne('/api/v1/things').flush({}, { status: 401, statusText: 'Unauthorized' });
    await tick();
    backend.expectOne('/api/v1/auth/refresh').flush(tokens(2));
    await tick();
    backend.expectOne('/api/v1/users/me').flush(me);
    await tick();

    const retry = backend.expectOne('/api/v1/things');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush(['ok']);
    expect(result).toEqual(['ok']);
  });

  it('logs the user out when the refresh fails', async () => {
    let failed = false;
    http.get('/api/v1/things').subscribe({ error: () => (failed = true) });

    backend.expectOne('/api/v1/things').flush({}, { status: 401, statusText: 'Unauthorized' });
    await tick();
    backend.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    await tick();

    expect(failed).toBe(true);
    expect(auth.isLoggedIn()).toBe(false);
  });
});
