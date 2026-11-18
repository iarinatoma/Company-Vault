import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  GuardResult,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom, isObservable, Observable } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { accessGuard } from './access-guard';

describe('accessGuard', () => {
  const state = { url: '/vaults/vault-1/edit' } as RouterStateSnapshot;
  const routeWith = (params: Record<string, string>) =>
    ({ paramMap: convertToParamMap(params) }) as unknown as ActivatedRouteSnapshot;

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideTestBackend()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function run(params: Record<string, string>): Observable<GuardResult> | GuardResult {
    const guard = accessGuard('VAULT', 'vaultId', 'WRITE');
    return TestBed.runInInjectionContext(() => guard(routeWith(params), state)) as
      | Observable<GuardResult>
      | GuardResult;
  }

  async function resolve(result: Observable<GuardResult> | GuardResult, level: string | null) {
    const pending = isObservable(result) ? firstValueFrom(result) : Promise.resolve(result);
    const request = http.expectOne('/api/v1/access/VAULT/vault-1/me');
    if (level === null) {
      request.flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    } else {
      request.flush({ resourceType: 'VAULT', resourceId: 'vault-1', level });
    }
    return pending;
  }

  it('lets the user through with the required level or higher', async () => {
    expect(await resolve(run({ vaultId: 'vault-1' }), 'WRITE')).toBe(true);
    expect(await resolve(run({ vaultId: 'vault-1' }), 'MANAGE')).toBe(true);
  });

  it('redirects to /home with a lower level', async () => {
    const result = await resolve(run({ vaultId: 'vault-1' }), 'READ');

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/home');
  });

  it('redirects to /home without any access', async () => {
    const result = await resolve(run({ vaultId: 'vault-1' }), 'NO_ACCESS');

    expect((result as UrlTree).toString()).toBe('/home');
  });

  it('redirects to /home when the level cannot be loaded', async () => {
    const result = await resolve(run({ vaultId: 'vault-1' }), null);

    expect((result as UrlTree).toString()).toBe('/home');
  });

  it('redirects to /home without asking the backend when the id is missing', () => {
    const result = run({});

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/home');
  });
});