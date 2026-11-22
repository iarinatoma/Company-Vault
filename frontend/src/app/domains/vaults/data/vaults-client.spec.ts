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


  it('creates a project vault', async () => {
    const input = { name: 'Webshop', description: null, ownerId: null };
    const created = firstValueFrom(client.createProject(input));
    const request = http.expectOne('/api/v1/vaults/projects');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush({ ...VAULT, type: 'PROJECT' });

    expect((await created).type).toBe('PROJECT');
  });

  it('adds, changes and removes members of a vault', async () => {
    const added = firstValueFrom(client.addMember('vault-1', 'ana@example.com', 'READ'));
    const add = http.expectOne('/api/v1/vaults/vault-1/members');
    expect(add.request.method).toBe('POST');
    expect(add.request.body).toEqual({ email: 'ana@example.com', accessLevel: 'READ' });
    add.flush({ userId: 'user-1', email: 'ana@example.com', accessLevel: 'READ' });
    expect((await added).userId).toBe('user-1');

    const updated = firstValueFrom(client.updateMember('vault-1', 'user-1', 'WRITE'));
    const update = http.expectOne('/api/v1/vaults/vault-1/members/user-1');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ accessLevel: 'WRITE' });
    update.flush({ userId: 'user-1', email: 'ana@example.com', accessLevel: 'WRITE' });
    await updated;

    const removed = firstValueFrom(client.removeMember('vault-1', 'user-1'));
    const remove = http.expectOne('/api/v1/vaults/vault-1/members/user-1');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
    await removed;
  });  
});