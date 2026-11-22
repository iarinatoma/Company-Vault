import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTestBackend } from '@app-testing/test-backend';
import { Vault } from '@app/vaults/models/vault';
import { VaultDetailStore } from './vault-detail-store';

const tick = () => new Promise((resolve) => setTimeout(resolve));

const vault = (accessLevel: Vault['accessLevel']): Vault => ({
  id: 'vault-1',
  name: 'Webshop',
  description: null,
  type: 'PROJECT',
  archived: false,
  createdAt: '2026-11-22T18:00:00Z',
  accessLevel,
});

const MANAGER = { userId: 'user-1', email: 'manager@example.com', accessLevel: 'MANAGE' as const };
const READER = { userId: 'user-2', email: 'reader@example.com', accessLevel: 'READ' as const };

describe('VaultDetailStore', () => {
  let store: VaultDetailStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    store = TestBed.inject(VaultDetailStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the vault and, for a manager, its members', async () => {
    const done = store.load('vault-1', false);
    http.expectOne('/api/v1/vaults/vault-1').flush(vault('MANAGE'));
    await tick();
    http.expectOne('/api/v1/vaults/vault-1/members').flush([MANAGER]);
    await done;

    expect(store.vault()?.name).toBe('Webshop');
    expect(store.canManageMembers()).toBe(true);
    expect(store.members()).toEqual([MANAGER]);
  });

  it('does not ask for the members when the user only has READ', async () => {
    const done = store.load('vault-1', false);
    http.expectOne('/api/v1/vaults/vault-1').flush(vault('READ'));
    await done;

    expect(store.canManageMembers()).toBe(false);
    expect(store.members()).toEqual([]);
  });

  it('lets an administrator see the members without access to the vault', async () => {
    const done = store.load('vault-1', true);
    http.expectOne('/api/v1/vaults/vault-1').flush(vault('NO_ACCESS'));
    await tick();
    http.expectOne('/api/v1/vaults/vault-1/members').flush([MANAGER]);
    await done;

    expect(store.canManageMembers()).toBe(true);
  });

  it('reloads the members after adding one', async () => {
    const loaded = store.load('vault-1', false);
    http.expectOne('/api/v1/vaults/vault-1').flush(vault('MANAGE'));
    await tick();
    http.expectOne('/api/v1/vaults/vault-1/members').flush([MANAGER]);
    await loaded;

    const added = store.addMember('reader@example.com', 'READ');
    http.expectOne({ method: 'POST', url: '/api/v1/vaults/vault-1/members' }).flush(READER);
    await tick();
    http.expectOne({ method: 'GET', url: '/api/v1/vaults/vault-1/members' }).flush([MANAGER, READER]);

    expect(await added).toBe(true);
    expect(store.members()).toEqual([MANAGER, READER]);
  });

  it('explains why the last manager cannot be removed', async () => {
    const loaded = store.load('vault-1', false);
    http.expectOne('/api/v1/vaults/vault-1').flush(vault('MANAGE'));
    await tick();
    http.expectOne('/api/v1/vaults/vault-1/members').flush([MANAGER]);
    await loaded;

    const removed = store.removeMember('user-1');
    http
      .expectOne({ method: 'DELETE', url: '/api/v1/vaults/vault-1/members/user-1' })
      .flush({ code: 'LAST_MANAGER' }, { status: 409, statusText: 'Conflict' });

    expect(await removed).toBe(false);
    expect(store.saveError()).toBe('A vault needs at least one member with MANAGE.');
  });
});