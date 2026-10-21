import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { User } from '@app/shared/auth/models/auth';
import { PageInfo, UserInput, UsersClient } from './users-client';

const PAGE_SIZE = 10;

type Status = 'idle' | 'loading' | 'saving';

/**
 * Lightweight signal store for the user list and its CRUD operations.
 *
 * Pattern: private writable signals hold the state, the public API is read-only signals plus
 * async methods. The server is the source of truth, so every successful write reloads the
 * current page instead of patching the list by hand (simple and always consistent).
 */
@Injectable({ providedIn: 'root' })
export class UsersStore {
  private readonly client = inject(UsersClient);

  private readonly _items = signal<readonly User[]>([]);
  private readonly _page = signal<PageInfo>({
    size: PAGE_SIZE,
    number: 0,
    totalElements: 0,
    totalPages: 0,
  });
  private readonly _status = signal<Status>('idle');
  private readonly _loadError = signal<string | null>(null);
  private readonly _saveError = signal<string | null>(null);

  readonly items = this._items.asReadonly();
  readonly page = this._page.asReadonly();
  readonly loadError = this._loadError.asReadonly();
  readonly saveError = this._saveError.asReadonly();

  readonly loading = computed(() => this._status() === 'loading');
  readonly saving = computed(() => this._status() === 'saving');
  readonly hasPrevious = computed(() => this._page().number > 0);
  readonly hasNext = computed(() => this._page().number < this._page().totalPages - 1);

  /** Loads a page (the current one by default). */
  async load(pageIndex: number = this._page().number): Promise<void> {
    this._status.set('loading');
    this._loadError.set(null);
    try {
      const result = await firstValueFrom(this.client.list(pageIndex, PAGE_SIZE));
      this._items.set(result.content);
      this._page.set(result.page);
    } catch (error) {
      this._loadError.set(describe(error));
    } finally {
      this._status.set('idle');
    }
  }

  previousPage(): Promise<void> {
    return this.hasPrevious() ? this.load(this._page().number - 1) : Promise.resolve();
  }

  nextPage(): Promise<void> {
    return this.hasNext() ? this.load(this._page().number + 1) : Promise.resolve();
  }

  /** Each write resolves to `true` on success so the caller can close its dialog. */
  create(input: UserInput): Promise<boolean> {
    return this.write(() => this.client.create(input), this._page().number);
  }

  update(id: string, input: UserInput): Promise<boolean> {
    return this.write(() => this.client.update(id, input), this._page().number);
  }

  remove(id: string): Promise<boolean> {
    // Deleting the last row of a later page would leave that page empty: step back one page.
    const page = this._page().number;
    const target = this._items().length === 1 && page > 0 ? page - 1 : page;
    return this.write(() => this.client.delete(id), target);
  }

  clearSaveError(): void {
    this._saveError.set(null);
  }

  private async write(
    action: () => Observable<unknown>,
    reloadPage: number,
  ): Promise<boolean> {
    this._status.set('saving');
    this._saveError.set(null);
    try {
      await firstValueFrom(action());
    } catch (error) {
      this._saveError.set(describe(error));
      this._status.set('idle');
      return false;
    }
    await this.load(reloadPage);
    return true;
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const code = (error.error as { code?: string } | null)?.code;
    switch (code) {
      case 'EMAIL_ALREADY_IN_USE':
        return $localize`:@@users.err.emailInUse:This e-mail address is already in use.`;
      case 'CANNOT_MODIFY_OWN_ACCOUNT':
        return $localize`:@@users.err.ownAccount:You cannot change your own role or delete your own account.`;
      case 'USER_NOT_FOUND':
        return $localize`:@@users.err.notFound:This user no longer exists.`;
      case 'VALIDATION_FAILED':
        return $localize`:@@users.err.validation:Please check your input.`;
      case 'FORBIDDEN':
        return $localize`:@@users.err.forbidden:You are not allowed to do this.`;
    }
    if (error.status === 0) return $localize`:@@users.err.unreachable:The server is not reachable.`;
  }
  return $localize`:@@users.err.generic:Something went wrong. Please try again.`;
}
