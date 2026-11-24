import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { hasAccess } from '@app/shared/auth/models/access';
import { VaultsClient } from '@app/vaults/data/vaults-client';
import { Vault } from '@app/vaults/models/vault';
import { FoldersClient } from '@app/secrets/data/folders-client';
import { Folder, MoveSecretInput, Secret } from '@app/secrets/models/secret';

/**
 * Modal dialog to move a secret:
 * - Within the same vault (between folders / root): shares the vault's DEK, no re-encryption.
 * - To another vault (requires MANAGE on source vault and WRITE on target vault): decrypts with
 *   the source vault's DEK and re-encrypts with the destination vault's DEK.
 */
@Component({
  selector: 'app-move-secret-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './move-secret-dialog.html',
})
export class MoveSecretDialog implements OnInit {
  readonly secret = input.required<Secret>();
  readonly currentVaultId = input.required<string>();
  readonly currentVaultFolders = input<readonly Folder[]>([]);
  readonly canMoveAcrossVaults = input(false);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<MoveSecretInput>();
  readonly closed = output<void>();

  private readonly vaultsClient = inject(VaultsClient);
  private readonly foldersClient = inject(FoldersClient);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly writableVaults = signal<readonly Vault[]>([]);
  protected readonly targetVaultId = signal<string>('');
  protected readonly targetFolderId = signal<string>('');
  protected readonly availableFolders = signal<readonly Folder[]>([]);
  protected readonly loadingFolders = signal(false);

  protected readonly isCrossVault = computed(
    () => this.targetVaultId() !== '' && this.targetVaultId() !== this.currentVaultId(),
  );

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (!el.open) el.showModal();
    });
  }

  async ngOnInit(): Promise<void> {
    this.targetVaultId.set(this.currentVaultId());
    this.targetFolderId.set(this.secret().folderId ?? '');
    this.availableFolders.set(this.currentVaultFolders());

    if (this.canMoveAcrossVaults()) {
      try {
        const vaults = await firstValueFrom(this.vaultsClient.list());
        this.writableVaults.set(
          vaults.filter((v) => !v.archived && hasAccess(v.accessLevel, 'WRITE')),
        );
      } catch {
        this.writableVaults.set([]);
      }
    }
  }

  protected async onVaultChange(event: Event): Promise<void> {
    const nextVaultId = (event.target as HTMLSelectElement).value;
    this.targetVaultId.set(nextVaultId);
    this.targetFolderId.set('');

    if (nextVaultId === this.currentVaultId()) {
      this.availableFolders.set(this.currentVaultFolders());
      return;
    }

    this.loadingFolders.set(true);
    try {
      this.availableFolders.set(await firstValueFrom(this.foldersClient.list(nextVaultId)));
    } catch {
      this.availableFolders.set([]);
    } finally {
      this.loadingFolders.set(false);
    }
  }

  protected onFolderChange(event: Event): void {
    this.targetFolderId.set((event.target as HTMLSelectElement).value);
  }

  protected save(): void {
    const destVault = this.targetVaultId();
    const destFolder = this.targetFolderId();
    this.submitted.emit({
      targetVaultId: destVault === '' || destVault === this.currentVaultId() ? null : destVault,
      targetFolderId: destFolder === '' ? null : destFolder,
    });
  }
}
