import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { Secret, SecretInput } from '@app/secrets/models/secret';
import { SecretsClient } from './secrets-client';

type Status = 'idle' | 'loading' | 'saving';

/**
 * State of one vault's secrets. Provided by the panel component (not root), so revealed values
 * live only as long as that page and are never shared with another vault. Values are fetched
 * only on an explicit "Show" and dropped again on "Hide", edit or delete.
 */
@Injectable()
export class SecretsStore {
  private readonly client = inject(SecretsClient);
  private vaultId: string | null = null;

  private readonly _items = signal<readonly Secret[]>([]);
  private readonly _revealed = signal<ReadonlyMap<string, string>>(new Map());
  private readonly _status = signal<Status>('idle');
  private readonly _loadError = signal<string | null>(null);
  private readonly _saveError = signal<string | null>(null);

  readonly items = this._items.asReadonly();
  /** Secret id → decrypted value, only for the secrets the user chose to show. */
  readonly revealed = this._revealed.asReadonly();
  readonly loadError = this._loadError.asReadonly();
  readonly saveError = this._saveError.asReadonly();
  readonly loading = computed(() => this._status() === 'loading');
  readonly saving = computed(() => this._status() === 'saving');

  async load(vaultId: string): Promise<void> {
    this.vaultId = vaultId;
    this._items.set([]);
    this._revealed.set(new Map());
    this._status.set('loading');
    this._loadError.set(null);
    try {
      this._items.set(await firstValueFrom(this.client.list(vaultId)));
    } catch (error) {
      this._loadError.set(describe(error));
    } finally {
      this._status.set('idle');
    }
  }

  async reveal(secretId: string): Promise<void> {
    if (this.vaultId === null) return;
    this._saveError.set(null);
    try {
      const value = await firstValueFrom(this.client.reveal(this.vaultId, secretId));
      this._revealed.update((values) => new Map(values).set(secretId, value));
    } catch (error) {
      this._saveError.set(describe(error));
    }
  }

  hide(secretId: string): void {
    this._revealed.update((values) => {
      const next = new Map(values);
      next.delete(secretId);
      return next;
    });
  }

  create(input: SecretInput): Promise<boolean> {
    return this.write((vaultId) => this.client.create(vaultId, input));
  }

  update(secretId: string, input: SecretInput): Promise<boolean> {
    this.hide(secretId);
    return this.write((vaultId) => this.client.update(vaultId, secretId, input));
  }

  remove(secretId: string): Promise<boolean> {
    this.hide(secretId);
    return this.write((vaultId) => this.client.delete(vaultId, secretId));
  }

  clearSaveError(): void {
    this._saveError.set(null);
  }

  private async write(action: (vaultId: string) => Observable<unknown>): Promise<boolean> {
    const vaultId = this.vaultId;
    if (vaultId === null) return false;
    this._status.set('saving');
    this._saveError.set(null);
    try {
      await firstValueFrom(action(vaultId));
      this._items.set(await firstValueFrom(this.client.list(vaultId)));
      return true;
    } catch (error) {
      this._saveError.set(describe(error));
      return false;
    } finally {
      this._status.set('idle');
    }
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const code = (error.error as { code?: string } | null)?.code;
    switch (code) {
      case 'SECRET_NOT_FOUND':
        return $localize`:@@secrets.err.notFound:This secret no longer exists.`;
      case 'VAULT_ARCHIVED':
        return $localize`:@@secrets.err.archived:This vault is archived and read-only.`;
      case 'VALIDATION_FAILED':
        return $localize`:@@secrets.err.validation:Please check your input.`;
      case 'FORBIDDEN':
        return $localize`:@@secrets.err.forbidden:You are not allowed to do this.`;
    }
    if (error.status === 0) return $localize`:@@secrets.err.unreachable:The server is not reachable.`;
  }
  return $localize`:@@secrets.err.generic:Something went wrong. Please try again.`;
}