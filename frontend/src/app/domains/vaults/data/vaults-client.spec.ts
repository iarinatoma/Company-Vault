import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { Vault } from '@app/vaults/models/vault';
import { VaultsClient } from './vaults-client';

const VAULT: Vault = {
  id: 'vault-1',
  name: 'IT Infrastructure',
  description: null,
  type: 'GENERAL',
  archived: false,
  createdAt: '2026-11-22T18:00:00Z',
  accessLevel: 'MANAGE',
};

describe('VaultsClient', () => {
  let client: VaultsClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    client = TestBed.inject(VaultsClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the vaults the caller may see', async () => {
    const vaults = firstValueFrom(client.list());
    const request = http.expectOne('/api/v1/vaults');

    expect(request.request.method).toBe('GET');
    request.flush([VAULT]);

    expect(await vaults).toEqual([VAULT]);
  });

  it('creates a general vault with its owner', async () => {
    const input = { name: 'IT Infrastructure', description: null, ownerId: 'user-1' };
    const created = firstValueFrom(client.create(input));
    const request = http.expectOne('/api/v1/vaults');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush(VAULT);

    expect(await created).toEqual(VAULT);
  });
});