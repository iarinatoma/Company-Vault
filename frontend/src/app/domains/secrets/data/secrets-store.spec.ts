import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTestBackend } from '@app-testing/test-backend';
import { Secret } from '@app/secrets/models/secret';
import { SecretsStore } from './secrets-store';

const tick = () => new Promise((resolve) => setTimeout(resolve));
const BASE = '/api/v1/vaults/vault-1/secrets';

const SECRET: Secret = {
  id: 'secret-1',
  vaultId: 'vault-1',
  name: 'Database',
  ownerEmail: 'owner@example.com',
  updatedByEmail: 'owner@example.com',
  createdAt: '2026-11-22T20:00:00Z',
  updatedAt: '2026-11-22T20:00:00Z',
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
    await loaded;
  });

  afterEach(() => http.verify());

  it('loads the secrets without any value', () => {
    expect(store.items()).toEqual([SECRET]);
    expect(store.revealed().size).toBe(0);
  });

  it('fetches a value only when shown and forgets it when hidden', async () => {
    const shown = store.reveal('secret-1');
    http.expectOne(`${BASE}/secret-1/value`).flush({ value: 's3cr3t' });
    await shown;
    expect(store.revealed().get('secret-1')).toBe('s3cr3t');

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

  it('reloads the list after creating a secret', async () => {
    const created = store.create({ name: 'API key', value: 'abc' });
    http.expectOne({ method: 'POST', url: BASE }).flush(SECRET);
    await tick();
    http.expectOne({ method: 'GET', url: BASE }).flush([SECRET, { ...SECRET, id: 'secret-2', name: 'API key' }]);

    expect(await created).toBe(true);
    expect(store.items().length).toBe(2);
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
    expect(await removed).toBe(true);
  });
});