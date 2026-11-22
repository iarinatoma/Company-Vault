import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CreateVaultInput, Vault } from '@app/vaults/models/vault';
import { VaultsClient } from './vaults-client';

type Status = 'idle' | 'loading' | 'saving';

/**
 * Signal store for the vault list. The server is the source of truth: after a successful
 * create the list is reloaded instead of patched by hand.
 */
@Injectable({ providedIn: 'root' })
export class VaultsStore {
  private readonly client = inject(VaultsClient);

  private readonly _items = signal<readonly Vault[]>([]);
  private readonly _status = signal<Status>('idle');
  private readonly _loadError = signal<string | null>(null);
  private readonly _saveError = signal<string | null>(null);

  readonly items = this._items.asReadonly();
  readonly loadError = this._loadError.asReadonly();
  readonly saveError = this._saveError.asReadonly();
  readonly loading = computed(() => this._status() === 'loading');
  readonly saving = computed(() => this._status() === 'saving');

  async load(): Promise<void> {
    this._status.set('loading');
    this._loadError.set(null);
    try {
      this._items.set(await firstValueFrom(this.client.list()));
    } catch (error) {
      this._loadError.set(describe(error));
    } finally {
      this._status.set('idle');
    }
  }

  /** Resolves to `true` on success so the caller can close its dialog. */
  async create(input: CreateVaultInput): Promise<boolean> {
    this._status.set('saving');
    this._saveError.set(null);
    try {
      await firstValueFrom(this.client.create(input));
    } catch (error) {
      this._saveError.set(describe(error));
      this._status.set('idle');
      return false;
    }
    await this.load();
    return true;
  }

  clearSaveError(): void {
    this._saveError.set(null);
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const code = (error.error as { code?: string } | null)?.code;
    switch (code) {
      case 'USER_NOT_FOUND':
        return $localize`:@@vaults.err.ownerNotFound:The chosen owner no longer exists.`;
      case 'VALIDATION_FAILED':
        return $localize`:@@vaults.err.validation:Please check your input.`;
      case 'FORBIDDEN':
        return $localize`:@@vaults.err.forbidden:You are not allowed to do this.`;
    }
    if (error.status === 0) return $localize`:@@vaults.err.unreachable:The server is not reachable.`;
  }
  return $localize`:@@vaults.err.generic:Something went wrong. Please try again.`;
}