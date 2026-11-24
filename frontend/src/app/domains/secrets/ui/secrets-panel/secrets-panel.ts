import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { ConfirmDialog } from '@app/home/ui/confirm-dialog/confirm-dialog';
import { AccessLevel, hasAccess } from '@app/shared/auth/models/access';
import { SecureClipboard } from '@app/secrets/data/secure-clipboard';
import { FolderSelection, SecretsStore } from '@app/secrets/data/secrets-store';
import {
  Folder,
  MoveSecretInput,
  Secret,
  SecretFields,
  SecretInput,
  SecretType,
} from '@app/secrets/models/secret';
import { MoveSecretDialog } from '@app/secrets/ui/move-secret-dialog/move-secret-dialog';
import { SecretForm } from '@app/secrets/ui/secret-form/secret-form';
import { SecretValue } from '@app/secrets/ui/secret-value/secret-value';
import { secretTypeLabel, toSecretType } from '@app/secrets/util/secret-fields';

/**
 * The secrets and folders of one vault, shown on the vault page. Buttons follow the caller's level
 * on the vault (READ → show/search, WRITE → create/edit/folders/move, MANAGE → delete/cross-vault move)
 * and an archived vault is read-only. Values and type-specific fields stay masked until "Show";
 * "Copy" decrypts on click and puts the value in the clipboard, which is emptied again shortly after.
 */
@Component({
  selector: 'app-secrets-panel',
  imports: [DatePipe, ConfirmDialog, SecretForm, MoveSecretDialog, SecretValue],
  providers: [SecretsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './secrets-panel.html',
})
export class SecretsPanel implements OnInit {
  readonly vaultId = input.required<string>();
  readonly accessLevel = input.required<AccessLevel>();
  readonly archived = input(false);

  protected readonly store = inject(SecretsStore);
  protected readonly clipboard = inject(SecureClipboard);

  protected readonly canWrite = computed(() => hasAccess(this.accessLevel(), 'WRITE') && !this.archived());
  protected readonly canManage = computed(() => hasAccess(this.accessLevel(), 'MANAGE') && !this.archived());

  protected readonly folderNameById = computed(() => {
    const map = new Map<string, string>();
    for (const folder of this.store.folders()) {
      map.set(folder.id, folder.name);
    }
    return map;
  });

  protected readonly activeFolder = computed(() => {
    const selected = this.store.selectedFolder();
    if (selected === 'ALL' || selected === 'ROOT') return null;
    return this.store.folders().find((f) => f.id === selected) ?? null;
  });

  protected readonly defaultNewSecretFolderId = computed(() => {
    const selected = this.store.selectedFolder();
    return selected === 'ALL' || selected === 'ROOT' ? null : selected;
  });

  protected readonly editing = signal<Secret | 'new' | null>(null);
  protected readonly editTarget = computed(() => {
    const target = this.editing();
    return target === 'new' ? null : target;
  });
  /**
   * The decrypted fields of the edited secret, taken when the dialog opens and only if the secret
   * is shown at that moment; then the form can change them. A snapshot, so saving (which hides the
   * secret) does not reset what the user typed.
   */
  protected readonly editFields = signal<SecretFields | null>(null);
  protected readonly moving = signal<Secret | null>(null);
  protected readonly deleting = signal<Secret | null>(null);

  protected readonly folderDialogMode = signal<'create' | 'rename' | null>(null);
  protected readonly folderNameInput = signal('');
  protected readonly deletingFolder = signal<Folder | null>(null);

  protected readonly deleteHeading = $localize`:@@secrets.deleteTitle:Delete secret?`;
  protected readonly deleteLabel = $localize`:@@secrets.confirmDelete:Delete`;
  protected readonly deleteFolderHeading = $localize`:@@folders.deleteTitle:Delete folder?`;
  protected readonly cancelLabel = $localize`:@@secrets.cancel:Cancel`;

  ngOnInit(): void {
    void this.store.load(this.vaultId());
  }

  protected selectFolder(selection: FolderSelection): void {
    this.store.selectFolder(selection);
  }

  protected onSearchInput(event: Event): void {
    this.store.setSearchQuery((event.target as HTMLInputElement).value);
  }

  protected toggle(secret: Secret): void {
    if (this.store.revealed().has(secret.id)) this.store.hide(secret.id);
    else void this.store.reveal(secret.id);
  }

  /** Copies the main value; a masked secret is decrypted for this click only and stays masked. */
  protected async copy(secret: Secret): Promise<void> {
    const revealed = this.store.revealed().get(secret.id) ?? (await this.store.fetchForCopy(secret.id));
    if (revealed) await this.clipboard.copy(revealed.value);
  }

  protected openCreate(): void {
    this.store.clearSaveError();
    this.editFields.set(null);
    this.editing.set('new');
  }

  protected openEdit(secret: Secret): void {
    this.store.clearSaveError();
    this.editFields.set(this.store.revealed().get(secret.id)?.fields ?? null);
    this.editing.set(secret);
  }

  protected openMove(secret: Secret): void {
    this.store.clearSaveError();
    this.moving.set(secret);
  }

  protected openDelete(secret: Secret): void {
    this.store.clearSaveError();
    this.deleting.set(secret);
  }

  protected async save(input: SecretInput): Promise<void> {
    const target = this.editing();
    if (target === null) return;
    const ok = target === 'new' ? await this.store.create(input) : await this.store.update(target.id, input);
    if (ok) this.editing.set(null);
  }

  protected async confirmMove(input: MoveSecretInput): Promise<void> {
    const target = this.moving();
    if (target === null) return;
    if (await this.store.moveSecret(target.id, input)) {
      this.moving.set(null);
    }
  }

  protected async confirmDelete(): Promise<void> {
    const target = this.deleting();
    if (target === null) return;
    if (await this.store.remove(target.id)) this.deleting.set(null);
  }

  protected openCreateFolder(): void {
    this.store.clearSaveError();
    this.folderNameInput.set('');
    this.folderDialogMode.set('create');
  }

  protected openRenameFolder(folder: Folder): void {
    this.store.clearSaveError();
    this.folderNameInput.set(folder.name);
    this.folderDialogMode.set('rename');
  }

  protected onFolderNameChange(event: Event): void {
    this.folderNameInput.set((event.target as HTMLInputElement).value);
  }

  protected async submitFolder(): Promise<void> {
    const mode = this.folderDialogMode();
    const name = this.folderNameInput().trim();
    if (!mode || !name) return;

    let ok = false;
    if (mode === 'create') {
      ok = await this.store.createFolder(name);
    } else {
      const folder = this.activeFolder();
      if (folder) ok = await this.store.renameFolder(folder.id, name);
    }
    if (ok) this.folderDialogMode.set(null);
  }

  protected openDeleteFolder(folder: Folder): void {
    this.store.clearSaveError();
    this.deletingFolder.set(folder);
  }

  protected async confirmDeleteFolder(): Promise<void> {
    const folder = this.deletingFolder();
    if (!folder) return;
    if (await this.store.removeFolder(folder.id)) {
      this.deletingFolder.set(null);
    }
  }

  protected typeOf(secret: Secret): SecretType {
    return toSecretType(secret.type);
  }

  protected typeLabel(secret: Secret): string {
    return secretTypeLabel(this.typeOf(secret));
  }

  protected isExpired(iso: string | null): boolean {
    if (!iso) return false;
    return new Date(iso).getTime() < Date.now();
  }
}
