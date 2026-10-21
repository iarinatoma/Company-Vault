import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideTestBackend } from '@app-testing/test-backend';
import { User } from '@app/shared/auth/models/auth';
import { UserPage } from './users-client';
import { UsersStore } from './users-store';

const user = (n: number): User => ({
  id: `id-${n}`,
  email: `user${n}@example.com`,
  role: 'USER',
  twoFactorEnabled: false,
  createdAt: '2026-08-12T00:00:00Z',
});

const page = (items: User[], number = 0, totalPages = 1, totalElements = items.length): UserPage => ({
  content: items,
  page: { size: 10, number, totalElements, totalPages },
});

const listUrl = (r: { url: string; method: string }) => r.url === '/api/v1/users' && r.method === 'GET';
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('UsersStore', () => {
  let store: UsersStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    store = TestBed.inject(UsersStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a page into read-only signals', async () => {
    const loading = store.load(0);
    expect(store.loading()).toBe(true);
    const req = http.expectOne(listUrl);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('sort')).toBe('email,asc');
    req.flush(page([user(1), user(2)]));
    await loading;

    expect(store.items().map((u) => u.email)).toEqual(['user1@example.com', 'user2@example.com']);
    expect(store.page().totalElements).toBe(2);
    expect(store.loading()).toBe(false);
    expect(store.loadError()).toBeNull();
  });

  it('exposes a load error', async () => {
    const loading = store.load(0);
    http.expectOne(listUrl).flush({}, { status: 500, statusText: 'Error' });
    await loading;

    expect(store.loadError()).toBeTruthy();
    expect(store.items()).toEqual([]);
  });

  it('navigates between pages', async () => {
    let loading = store.load(0);
    http.expectOne(listUrl).flush(page([user(1)], 0, 2, 11));
    await loading;
    expect(store.hasPrevious()).toBe(false);
    expect(store.hasNext()).toBe(true);

    loading = store.nextPage();
    const req = http.expectOne(listUrl);
    expect(req.request.params.get('page')).toBe('1');
    req.flush(page([user(11)], 1, 2, 11));
    await loading;

    expect(store.hasNext()).toBe(false);
    expect(store.hasPrevious()).toBe(true);
  });

  it('creates a user and reloads the current page', async () => {
    const created = store.create({ email: 'new@example.com', role: 'USER', password: 'long-password-123' });
    expect(store.saving()).toBe(true);
    const post = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/users');
    expect(post.request.body).toEqual({ email: 'new@example.com', role: 'USER', password: 'long-password-123' });
    post.flush(user(3));
    await tick();
    http.expectOne(listUrl).flush(page([user(3)]));

    expect(await created).toBe(true);
    expect(store.items()).toHaveLength(1);
    expect(store.saving()).toBe(false);
  });

  it('updates a user with PUT', async () => {
    const updated = store.update('id-1', { email: 'a@example.com', role: 'MANAGER' });
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/v1/users/id-1');
    expect(put.request.body).toEqual({ email: 'a@example.com', role: 'MANAGER' });
    put.flush(user(1));
    await tick();
    http.expectOne(listUrl).flush(page([user(1)]));

    expect(await updated).toBe(true);
  });

  it('reports a backend error code as a message and keeps the list untouched', async () => {
    const created = store.create({ email: 'taken@example.com', role: 'USER', password: 'long-password-123' });
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ code: 'EMAIL_ALREADY_IN_USE' }, { status: 409, statusText: 'Conflict' });

    expect(await created).toBe(false);
    expect(store.saveError()).toContain('e-mail');
    expect(store.saving()).toBe(false);

    store.clearSaveError();
    expect(store.saveError()).toBeNull();
  });

  it('deletes a user and steps back when the last row of a later page is removed', async () => {
    const loading = store.load(1);
    http.expectOne(listUrl).flush(page([user(11)], 1, 2, 11));
    await loading;

    const removed = store.remove('id-11');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/v1/users/id-11').flush(null);
    await tick();
    const reload = http.expectOne(listUrl);
    expect(reload.request.params.get('page')).toBe('0');
    reload.flush(page([user(1)], 0, 1, 10));

    expect(await removed).toBe(true);
  });
});
