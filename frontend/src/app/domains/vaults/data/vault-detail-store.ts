import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import { AccessLevel, hasAccess } from '@app/shared/auth/models/access';
import { Vault, VaultMember } from '@app/vaults/models/vault';
import { VaultsClient } from './vaults-client';

type Status = 'idle' | 'loading' | 'saving';

/**
 * State of one vault's page: its metadata and, for managers and administrators, its members.
 * After every member change the member list is reloaded from the server (the source of truth).
 */
@Injectable({ providedIn: 'root' })
export class VaultDetailStore {
  private readonly client = inject(VaultsClient);

  private readonly _vault = signal<Vault | null>(null);
  private readonly _members = signal<readonly VaultMember[]>([]);
  private readonly _canManageMembers = signal(false);
  private readonly _status = signal<Status>('idle');
  private readonly _loadError = signal<string | null>(null);
  private readonly _saveError = signal<string | null>(null);

  readonly vault = this._vault.asReadonly();
  readonly members = this._members.asReadonly();
  /** Managers of the vault — and administrators, to recover access. */
  readonly canManageMembers = this._canManageMembers.asReadonly();
  readonly loadError = this._loadError.asReadonly();
  readonly saveError = this._saveError.asReadonly();
  readonly loading = computed(() => this._status() === 'loading');
  readonly saving = computed(() => this._status() === 'saving');

  async load(vaultId: string, isAdmin: boolean): Promise<void> {
    this._status.set('loading');
    this._loadError.set(null);
    this._saveError.set(null);
    this._vault.set(null);
    this._members.set([]);
    try {
      const vault = await firstValueFrom(this.client.get(vaultId));
      this._vault.set(vault);
      const canManage = isAdmin || hasAccess(vault.accessLevel, 'MANAGE');
      this._canManageMembers.set(canManage);
      if (canManage) this._members.set(await firstValueFrom(this.client.members(vaultId)));
    } catch (error) {
      this._loadError.set(describe(error));
    } finally {
      this._status.set('idle');
    }
  }

  addMember(email: string, level: AccessLevel): Promise<boolean> {
    return this.write((id) => this.client.addMember(id, email, level));
  }

  updateMember(userId: string, level: AccessLevel): Promise<boolean> {
    return this.write((id) => this.client.updateMember(id, userId, level));
  }

  removeMember(userId: string): Promise<boolean> {
    return this.write((id) => this.client.removeMember(id, userId));
  }

  update(name: string, description: string | null): Promise<boolean> {
    return this.writeVault((id) => this.client.update(id, name, description));
  }

  archive(): Promise<boolean> {
    return this.writeVault((id) => this.client.archive(id));
  }

  delete(): Promise<boolean> {
    return this.writeVault((id) => this.client.delete(id));
  }

  clearSaveError(): void {
    this._saveError.set(null);
  }

  private async write(action: (vaultId: string) => Observable<unknown>): Promise<boolean> {
    const vault = this._vault();
    if (vault === null) return false;
    this._status.set('saving');
    this._saveError.set(null);
    try {
      await firstValueFrom(action(vault.id));
      this._members.set(await firstValueFrom(this.client.members(vault.id)));
      return true;
    } catch (error) {
      this._saveError.set(describe(error));
      return false;
    } finally {
      this._status.set('idle');
    }
  }

  private async writeVault(action: (vaultId: string) => Observable<unknown>): Promise<boolean> {
    const vault = this._vault();
    if (vault === null) return false;
    this._status.set('saving');
    this._saveError.set(null);
    try {
      const updated = await firstValueFrom(action(vault.id));
      if (updated && typeof updated === 'object' && 'id' in updated) {
        this._vault.set(updated as Vault);
      } else {
        this._vault.set(null);
      }
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
      case 'LAST_MANAGER':
        return $localize`:@@vaultDetail.err.lastManager:A vault needs at least one member with MANAGE.`;
      case 'USER_NOT_FOUND':
        return $localize`:@@vaultDetail.err.userNotFound:There is no user with this e-mail address.`;
      case 'VALIDATION_FAILED':
        return $localize`:@@vaultDetail.err.validation:Please check your input. Auditors cannot be given access.`;
      case 'VAULT_NOT_FOUND':
        return $localize`:@@vaultDetail.err.vaultNotFound:This vault does not exist.`;
      case 'FORBIDDEN':
        return $localize`:@@vaultDetail.err.forbidden:You do not have access to this vault.`;
    }
    if (error.status === 0) return $localize`:@@vaultDetail.err.unreachable:The server is not reachable.`;
  }
  return $localize`:@@vaultDetail.err.generic:Something went wrong. Please try again.`;
}