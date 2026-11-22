import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { ConfirmDialog } from '@app/home/ui/confirm-dialog/confirm-dialog';
import { AccessLevel, hasAccess } from '@app/shared/auth/models/access';
import { SecretsStore } from '@app/secrets/data/secrets-store';
import { Secret, SecretInput } from '@app/secrets/models/secret';
import { SecretForm } from '@app/secrets/ui/secret-form/secret-form';

/**
 * The secrets of one vault, shown on the vault page. Buttons follow the caller's level on the vault
 * (READ → show, WRITE → create/edit, MANAGE → delete) and an archived vault is read-only; the backend
 * checks every request again. Values stay masked until "Show".
 */
@Component({
  selector: 'app-secrets-panel',
  imports: [DatePipe, ConfirmDialog, SecretForm],
  providers: [SecretsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './secrets-panel.html',
})
export class SecretsPanel implements OnInit {
  readonly vaultId = input.required<string>();
  readonly accessLevel = input.required<AccessLevel>();
  readonly archived = input(false);

  protected readonly store = inject(SecretsStore);

  protected readonly canWrite = computed(() => hasAccess(this.accessLevel(), 'WRITE') && !this.archived());
  protected readonly canDelete = computed(() => hasAccess(this.accessLevel(), 'MANAGE') && !this.archived());

  protected readonly editing = signal<Secret | 'new' | null>(null);
  protected readonly editTarget = computed(() => {
    const target = this.editing();
    return target === 'new' ? null : target;
  });
  protected readonly deleting = signal<Secret | null>(null);

  protected readonly deleteHeading = $localize`:@@secrets.deleteTitle:Delete secret?`;
  protected readonly deleteLabel = $localize`:@@secrets.confirmDelete:Delete`;
  protected readonly cancelLabel = $localize`:@@secrets.cancel:Cancel`;

  ngOnInit(): void {
    void this.store.load(this.vaultId());
  }

  protected toggle(secret: Secret): void {
    if (this.store.revealed().has(secret.id)) this.store.hide(secret.id);
    else void this.store.reveal(secret.id);
  }

  protected openCreate(): void {
    this.store.clearSaveError();
    this.editing.set('new');
  }

  protected openEdit(secret: Secret): void {
    this.store.clearSaveError();
    this.editing.set(secret);
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

  protected async confirmDelete(): Promise<void> {
    const target = this.deleting();
    if (target === null) return;
    if (await this.store.remove(target.id)) this.deleting.set(null);
  }
}