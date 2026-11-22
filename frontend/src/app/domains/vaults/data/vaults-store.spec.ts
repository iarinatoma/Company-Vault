import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTestBackend } from '@app-testing/test-backend';
import { Vault } from '@app/vaults/models/vault';
import { VaultsStore } from './vaults-store';

const VAULT: Vault = {
  id: 'vault-1',
  name: 'Shared Accounts',
  description: 'Team logins',
  type: 'GENERAL',
  archived: false,
  createdAt: '2026-11-22T18:00:00Z',
  accessLevel: 'WRITE',
};

describe('VaultsStore', () => {
  let store: VaultsStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    store = TestBed.inject(VaultsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the vault list', async () => {
    const done = store.load();
    expect(store.loading()).toBe(true);
    http.expectOne('/api/v1/vaults').flush([VAULT]);
    await done;

    expect(store.items()).toEqual([VAULT]);
    expect(store.loading()).toBe(false);
  });

  it('shows a readable message when loading fails', async () => {
    const done = store.load();
    http.expectOne('/api/v1/vaults').flush(null, { status: 0, statusText: 'Unknown Error' });
    await done;

    expect(store.loadError()).toBe('The server is not reachable.');
  });

  it('reloads the list after creating a vault', async () => {
    const done = store.create({ name: 'Shared Accounts', description: null, ownerId: 'user-1' });
    http.expectOne({ method: 'POST', url: '/api/v1/vaults' }).flush(VAULT);
    await new Promise((resolve) => setTimeout(resolve));
    http.expectOne({ method: 'GET', url: '/api/v1/vaults' }).flush([VAULT]);

    expect(await done).toBe(true);
    expect(store.items()).toEqual([VAULT]);
  });

  it('keeps the dialog open with a message when the owner is unknown', async () => {
    const done = store.create({ name: 'X', description: null, ownerId: 'gone' });
    http
      .expectOne({ method: 'POST', url: '/api/v1/vaults' })
      .flush({ code: 'USER_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect(await done).toBe(false);
    expect(store.saveError()).toBe('The chosen owner no longer exists.');
  });
});