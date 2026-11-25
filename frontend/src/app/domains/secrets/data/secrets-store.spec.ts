import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTestBackend } from '@app-testing/test-backend';
import { Folder, Secret } from '@app/secrets/models/secret';
import { SecretsStore } from './secrets-store';

const tick = () => new Promise((resolve) => setTimeout(resolve));
const BASE = '/api/v1/vaults/vault-1/secrets';
const FOLDERS_BASE = '/api/v1/vaults/vault-1/folders';

const SECRET: Secret = {
  id: 'secret-1',
  vaultId: 'vault-1',
  folderId: null,
  name: 'Database',
  type: 'API_KEY',
  description: 'Primary DB',
  tags: ['prod', 'db'],
  expiresAt: null,
  ownerEmail: 'owner@example.com',
  updatedByEmail: 'owner@example.com',
  createdAt: '2026-11-22T20:00:00Z',
  updatedAt: '2026-11-22T20:00:00Z',
};

const FOLDER: Folder = {
  id: 'folder-1',
  vaultId: 'vault-1',
  name: 'Production',
  secretCount: 0,
  createdByEmail: 'owner@example.com',
  createdAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:00:00Z',
};

describe('SecretsStore', () => {
  let store: SecretsStore;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [SecretsStore, ...provideTestBackend()] });
    store = TestBed.inject(SecretsStore);
    http = TestBed.inject(HttpTestingController);

    const loaded = store.load('vault-1');
    http.expectOne(BASE).flush([SECRET]);
    http.expectOne(FOLDERS_BASE).flush([FOLDER]);
    await loaded;
  });

  afterEach(() => http.verify());

  it('loads the secrets and folders without any value', () => {
    expect(store.items()).toEqual([SECRET]);
    expect(store.folders()).toEqual([FOLDER]);
    expect(store.revealed().size).toBe(0);
  });

  it('filters secrets by folder, search query and tag without decrypting', () => {
    store.selectFolder('folder-1');
    expect(store.filteredItems()).toEqual([]);

    store.selectFolder('ROOT');
    expect(store.filteredItems()).toEqual([SECRET]);

    store.setSearchQuery('prod');
    expect(store.filteredItems()).toEqual([SECRET]);

    store.toggleTagFilter('db');
    expect(store.filteredItems()).toEqual([SECRET]);

    store.toggleTagFilter('missing');
    expect(store.filteredItems()).toEqual([]);
  });

  it('fetches a value only when shown and forgets it when hidden', async () => {
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({ value: 's3cr3t' });
    await shown;
    expect(store.revealed().get('secret-1')?.value).toBe('s3cr3t');

    store.hide('secret-1');
    expect(store.revealed().has('secret-1')).toBe(false);
  });

  it('does not expose previously loaded secrets after access to another vault is forbidden', async () => {
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({ value: 's3cr3t' });
    await shown;

    const loaded = store.load('vault-2');
    http
      .expectOne('/api/v1/vaults/vault-2/secrets')
      .flush({ code: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    http
      .expectOne('/api/v1/vaults/vault-2/folders')
      .flush({ code: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    await loaded;

    expect(store.items()).toEqual([]);
    expect(store.revealed().size).toBe(0);
    expect(store.loadError()).toBe('You are not allowed to do this.');
  });

  it('does not cache a secret value when the server forbids revealing it', async () => {
    const shown = store.reveal('secret-1');
    http
      .expectOne(`${BASE}/secret-1/value`)
      .flush({ code: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    await shown;

    expect(store.revealed().has('secret-1')).toBe(false);
    expect(store.saveError()).toBe('You are not allowed to do this.');
  });

  it('reloads secrets and folders after creating a secret', async () => {
    const created = store.create({ name: 'API key', value: 'abc' });
    http.expectOne({ method: 'POST', url: BASE }).flush(SECRET);
    await tick();
    http.expectOne({ method: 'GET', url: BASE }).flush([SECRET, { ...SECRET, id: 'secret-2', name: 'API key' }]);
    http.expectOne({ method: 'GET', url: FOLDERS_BASE }).flush([FOLDER]);

    expect(await created).toBe(true);
    expect(store.items().length).toBe(2);
  });

  it('moves a secret and reloads lists', async () => {
    const moved = store.moveSecret('secret-1', { targetVaultId: null, targetFolderId: 'folder-1' });
    const req = http.expectOne({ method: 'POST', url: `${BASE}/secret-1/move` });
    expect(req.request.body).toEqual({ targetVaultId: null, targetFolderId: 'folder-1' });
    req.flush({ ...SECRET, folderId: 'folder-1' });
    await tick();
    http.expectOne({ method: 'GET', url: BASE }).flush([{ ...SECRET, folderId: 'folder-1' }]);
    http.expectOne({ method: 'GET', url: FOLDERS_BASE }).flush([{ ...FOLDER, secretCount: 1 }]);

    expect(await moved).toBe(true);
    expect(store.items()[0].folderId).toBe('folder-1');
  });

  it('explains that an archived vault is read-only', async () => {
    const created = store.create({ name: 'X', value: 'v' });
    http
      .expectOne({ method: 'POST', url: BASE })
      .flush({ code: 'VAULT_ARCHIVED' }, { status: 409, statusText: 'Conflict' });

    expect(await created).toBe(false);
    expect(store.saveError()).toBe('This vault is archived and read-only.');
  });

  it('forgets a shown value when the secret is deleted', async () => {
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({ value: 's3cr3t' });
    await shown;

    const removed = store.remove('secret-1');
    expect(store.revealed().has('secret-1')).toBe(false);
    http.expectOne({ method: 'DELETE', url: `${BASE}/secret-1` }).flush(null);
    await tick();
    http.expectOne({ method: 'GET', url: BASE }).flush([]);
    http.expectOne({ method: 'GET', url: FOLDERS_BASE }).flush([FOLDER]);
    expect(await removed).toBe(true);
  });


  it('matches search query against description case-insensitively', () => {
    store.setSearchQuery('PRIMARY db');
    expect(store.filteredItems()).toEqual([SECRET]);

    store.setSearchQuery('nonexistent description');
    expect(store.filteredItems()).toEqual([]);
  });

  it('never matches revealed secret values when searching', async () => {
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({ value: 'top-secret-password-999' });
    await shown;
    expect(store.revealed().get('secret-1')?.value).toBe('top-secret-password-999');

    store.setSearchQuery('top-secret-password-999');
    expect(store.filteredItems()).toEqual([]);
  });

  it('keeps the revealed fields with the value and forgets both when hidden', async () => {
    const fields = { username: 'alice', url: 'https://mail.example.com', customFields: [] };
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({ value: 'pa55word', fields });
    await shown;
    expect(store.revealed().get('secret-1')).toEqual({ value: 'pa55word', fields });

    store.hide('secret-1');
    expect(store.revealed().has('secret-1')).toBe(false);
  });

  it('never matches revealed field values when searching', async () => {
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({
      value: 'pa55word',
      fields: { username: 'alice-the-admin', url: null, customFields: [{ name: 'PIN', value: '9876' }] },
    });
    await shown;

    store.setSearchQuery('alice-the-admin');
    expect(store.filteredItems()).toEqual([]);
    store.setSearchQuery('9876');
    expect(store.filteredItems()).toEqual([]);
  });
});
