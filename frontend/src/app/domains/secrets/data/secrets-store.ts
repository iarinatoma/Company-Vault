import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { Folder, MoveSecretInput, RevealedSecret, Secret, SecretInput } from '@app/secrets/models/secret';
import { FoldersClient } from './folders-client';
import { SecretsClient } from './secrets-client';

/** How long a shown value stays visible before it is masked again. */
export const REVEAL_TIMEOUT_MS = new InjectionToken<number>('REVEAL_TIMEOUT_MS', {
  providedIn: 'root',
  factory: () => 60 * 1000,
});

type Status = 'idle' | 'loading' | 'saving';
export type FolderSelection = 'ALL' | 'ROOT' | string;

/**
 * State of one vault's folders and secrets. Provided by the panel component (not root),
 * so revealed values live only as long as that page and are never shared with another vault.
 * A shown value is masked again after `REVEAL_TIMEOUT_MS`.
 */
@Injectable()
export class SecretsStore {
  private readonly client = inject(SecretsClient);
  private readonly foldersClient = inject(FoldersClient);
  private readonly revealTimeoutMs = inject(REVEAL_TIMEOUT_MS);
  private vaultId: string | null = null;
  /** Secret id → timer that masks the shown value again. */
  private readonly hideTimers = new Map<string, ReturnType<typeof setTimeout>>();

  private readonly _items = signal<readonly Secret[]>([]);
  private readonly _folders = signal<readonly Folder[]>([]);
  private readonly _selectedFolder = signal<FolderSelection>('ALL');
  private readonly _searchQuery = signal('');
  private readonly _selectedTag = signal<string | null>(null);
  private readonly _revealed = signal<ReadonlyMap<string, RevealedSecret>>(new Map());
  private readonly _status = signal<Status>('idle');
  private readonly _loadError = signal<string | null>(null);
  private readonly _saveError = signal<string | null>(null);

  readonly items = this._items.asReadonly();
  readonly folders = this._folders.asReadonly();
  readonly selectedFolder = this._selectedFolder.asReadonly();
  readonly searchQuery = this._searchQuery.asReadonly();
  readonly selectedTag = this._selectedTag.asReadonly();
  /** Secret id → decrypted value and fields, only for the secrets the user chose to show. */
  readonly revealed = this._revealed.asReadonly();
  readonly loadError = this._loadError.asReadonly();
  readonly saveError = this._saveError.asReadonly();
  readonly loading = computed(() => this._status() === 'loading');
  readonly saving = computed(() => this._status() === 'saving');

  /** All distinct tags present in the currently loaded secrets, sorted alphabetically. */
  readonly availableTags = computed(() => {
    const tags = new Set<string>();
    for (const secret of this._items()) {
      for (const tag of secret.tags ?? []) {
        tags.add(tag);
      }
    }
    return [...tags].sort((a, b) => a.localeCompare(b));
  });

  /** Client-side filtered view over plain-text metadata only (name, description, tags) — never secret values. */
  readonly filteredItems = computed(() => {
    const folder = this._selectedFolder();
    const query = this._searchQuery().trim().toLowerCase();
    const tag = this._selectedTag()?.trim().toLowerCase() ?? null;

    return this._items().filter((secret) => {
      if (folder === 'ROOT' && secret.folderId !== null) return false;
      if (folder !== 'ALL' && folder !== 'ROOT' && secret.folderId !== folder) return false;
      if (tag && !(secret.tags ?? []).includes(tag)) return false;
      if (query !== '') {
        const inName = secret.name.toLowerCase().includes(query);
        const inDescription = (secret.description ?? '').toLowerCase().includes(query);
        const inTags = (secret.tags ?? []).some((t) => t.toLowerCase().includes(query));
        if (!inName && !inDescription && !inTags) return false;
      }
      return true;
    });
  });

  readonly rootSecretCount = computed(() => this._items().filter((s) => s.folderId === null).length);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopHideTimers());
  }

  async load(vaultId: string): Promise<void> {
    this.vaultId = vaultId;
    this._items.set([]);
    this.stopHideTimers();
    this._revealed.set(new Map());
    this._selectedFolder.set('ALL');
    this._searchQuery.set('');
    this._selectedTag.set(null);
    this._status.set('loading');
    this._loadError.set(null);
    try {
      const [secrets, folders] = await Promise.all([
        firstValueFrom(this.client.list(vaultId)),
        firstValueFrom(this.foldersClient.list(vaultId)),
      ]);
      this._items.set(secrets);
      this._folders.set(folders);
    } catch (error) {
      this._loadError.set(describe(error));
    } finally {
      this._status.set('idle');
    }
  }

  selectFolder(selection: FolderSelection): void {
    this._selectedFolder.set(selection);
  }

  setSearchQuery(query: string): void {
    this._searchQuery.set(query);
  }

  toggleTagFilter(tag: string): void {
    this._selectedTag.update((current) => (current === tag ? null : tag));
  }

  clearFilters(): void {
    this._selectedFolder.set('ALL');
    this._searchQuery.set('');
    this._selectedTag.set(null);
  }

  async reveal(secretId: string): Promise<void> {
    if (this.vaultId === null) return;
    this._saveError.set(null);
    try {
      const revealed = await firstValueFrom(this.client.reveal(this.vaultId, secretId));
      this._revealed.update((values) => new Map(values).set(secretId, revealed));
      this.startHideTimer(secretId);
    } catch (error) {
      this._saveError.set(describe(error));
    }
  }

  /**
   * Decrypts one secret for "Copy" without showing it: the value goes to the clipboard only and
   * stays masked in the list. Null (with an error message) if the server refuses.
   */
  async fetchForCopy(secretId: string): Promise<RevealedSecret | null> {
    if (this.vaultId === null) return null;
    this._saveError.set(null);
    try {
      return await firstValueFrom(this.client.reveal(this.vaultId, secretId));
    } catch (error) {
      this._saveError.set(describe(error));
      return null;
    }
  }

  hide(secretId: string): void {
    const timer = this.hideTimers.get(secretId);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.hideTimers.delete(secretId);
    }
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

  moveSecret(secretId: string, input: MoveSecretInput): Promise<boolean> {
    this.hide(secretId);
    return this.write((vaultId) => this.client.move(vaultId, secretId, input));
  }

  remove(secretId: string): Promise<boolean> {
    this.hide(secretId);
    return this.write((vaultId) => this.client.delete(vaultId, secretId));
  }

  createFolder(name: string): Promise<boolean> {
    return this.write((vaultId) => this.foldersClient.create(vaultId, { name }));
  }

  renameFolder(folderId: string, name: string): Promise<boolean> {
    return this.write((vaultId) => this.foldersClient.update(vaultId, folderId, { name }));
  }

  async removeFolder(folderId: string): Promise<boolean> {
    const ok = await this.write((vaultId) => this.foldersClient.delete(vaultId, folderId));
    if (ok && this._selectedFolder() === folderId) {
      this._selectedFolder.set('ALL');
    }
    return ok;
  }

  clearSaveError(): void {
    this._saveError.set(null);
  }

  private startHideTimer(secretId: string): void {
    const previous = this.hideTimers.get(secretId);
    if (previous !== undefined) clearTimeout(previous);
    this.hideTimers.set(secretId, setTimeout(() => this.hide(secretId), this.revealTimeoutMs));
  }

  private stopHideTimers(): void {
    for (const timer of this.hideTimers.values()) clearTimeout(timer);
    this.hideTimers.clear();
  }

  private async write(action: (vaultId: string) => Observable<unknown>): Promise<boolean> {
    const vaultId = this.vaultId;
    if (vaultId === null) return false;
    this._status.set('saving');
    this._saveError.set(null);
    try {
      await firstValueFrom(action(vaultId));
      const [secrets, folders] = await Promise.all([
        firstValueFrom(this.client.list(vaultId)),
        firstValueFrom(this.foldersClient.list(vaultId)),
      ]);
      this._items.set(secrets);
      this._folders.set(folders);
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
      case 'FOLDER_NOT_FOUND':
        return $localize`:@@secrets.err.folderNotFound:This folder no longer exists.`;
      case 'FOLDER_NAME_ALREADY_EXISTS':
        return $localize`:@@secrets.err.folderDuplicate:A folder with this name already exists in this vault.`;
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
