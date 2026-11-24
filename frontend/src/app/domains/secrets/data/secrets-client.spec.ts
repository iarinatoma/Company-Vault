import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { SecretsClient } from './secrets-client';

const BASE = '/api/v1/vaults/vault-1/secrets';

describe('SecretsClient', () => {
  let client: SecretsClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    client = TestBed.inject(SecretsClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reveals a value through its own endpoint', async () => {
    const value = firstValueFrom(client.reveal('vault-1', 'secret-1'));
    const request = http.expectOne(`${BASE}/secret-1/value`);

    expect(request.request.method).toBe('GET');
    request.flush({ value: 's3cr3t' });

    expect(await value).toBe('s3cr3t');
  });

  it('sends the value only in the request body', async () => {
    const created = firstValueFrom(client.create('vault-1', { name: 'Database', value: 's3cr3t' }));
    const request = http.expectOne(BASE);

    expect(request.request.method).toBe('POST');
    expect(request.request.url).not.toContain('s3cr3t');
    expect(request.request.body).toEqual({ name: 'Database', value: 's3cr3t' });
    request.flush({ id: 'secret-1' });
    await created;
  });

  it('edits and deletes a secret', async () => {
    const updated = firstValueFrom(client.update('vault-1', 'secret-1', { name: 'DB', value: null }));
    const put = http.expectOne(`${BASE}/secret-1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ name: 'DB', value: null });
    put.flush({ id: 'secret-1' });
    await updated;

    const deleted = firstValueFrom(client.delete('vault-1', 'secret-1'));
    const del = http.expectOne(`${BASE}/secret-1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    await deleted;
  });
});