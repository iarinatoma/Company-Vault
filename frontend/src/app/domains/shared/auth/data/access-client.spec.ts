import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { AccessClient } from './access-client';

describe('AccessClient', () => {
  let client: AccessClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    client = TestBed.inject(AccessClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("asks the backend for the caller's own level on a resource", async () => {
    const level = firstValueFrom(client.myLevel('VAULT', 'vault-1'));
    const request = http.expectOne('/api/v1/access/VAULT/vault-1/me');

    expect(request.request.method).toBe('GET');
    request.flush({ resourceType: 'VAULT', resourceId: 'vault-1', level: 'WRITE' });

    expect(await level).toEqual({ resourceType: 'VAULT', resourceId: 'vault-1', level: 'WRITE' });
  });
});