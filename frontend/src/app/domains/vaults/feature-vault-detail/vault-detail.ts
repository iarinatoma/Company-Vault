import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { email, form, FormField, required, schema, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { ConfirmDialog } from '@app/home/ui/confirm-dialog/confirm-dialog';
import { AuthStore } from '@app/shared/auth/data/auth-store';
import { AccessLevel, hasAccess } from '@app/shared/auth/models/access';
import { VaultDetailStore } from '@app/vaults/data/vault-detail-store';
import { VaultMember } from '@app/vaults/models/vault';
import { SecretsPanel } from '@app/secrets/ui/secrets-panel/secrets-panel';

interface MemberFormModel {
  email: string;
  accessLevel: AccessLevel;
}

/** One vault: its metadata and, for managers and administrators, who has access (spec 4.4). */
@Component({
  selector: 'app-vault-detail',
  imports: [DatePipe, RouterLink, FormField, ConfirmDialog, SecretsPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vault-detail.html',
})
export class VaultDetail implements OnInit {
  /** From the route `/vaults/:vaultId` (component input binding). */
  readonly vaultId = input.required<string>();

  protected readonly store = inject(VaultDetailStore);
  private readonly user = inject(AuthStore).user;

  protected readonly isAdmin = computed(() => this.user()?.role === 'ADMINISTRATOR');
  protected readonly hasAccess = hasAccess;  
  protected readonly grantableLevels: readonly AccessLevel[] = ['READ', 'WRITE', 'MANAGE'];
  protected readonly removing = signal<VaultMember | null>(null);

  protected readonly removeHeading = $localize`:@@vaultDetail.removeTitle:Remove member?`;
  protected readonly removeLabel = $localize`:@@vaultDetail.confirmRemove:Remove`;
  protected readonly cancelLabel = $localize`:@@vaultDetail.cancel:Cancel`;

  protected readonly newMember = signal<MemberFormModel>({ email: '', accessLevel: 'READ' });
  protected readonly memberForm = form(
    this.newMember,
    schema<MemberFormModel>((path) => {
      required(path.email);
      email(path.email);
    }),
  );

  ngOnInit(): void {
    void this.store.load(this.vaultId(), this.isAdmin());
  }

  protected async addMember(): Promise<void> {
    await submit(this.memberForm, async () => {
      const { email, accessLevel } = this.newMember();
      if (await this.store.addMember(email.trim(), accessLevel)) {
        this.newMember.set({ email: '', accessLevel: 'READ' });
      }
    });
  }

  protected async changeLevel(member: VaultMember, event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    const level = select.value as AccessLevel;
    if (level === member.accessLevel) return;
    // If the server refuses (e.g. LAST_MANAGER), show the real level again.
    if (!(await this.store.updateMember(member.userId, level))) select.value = member.accessLevel;
  }

  protected openRemove(member: VaultMember): void {
    this.store.clearSaveError();
    this.removing.set(member);
  }

  protected async confirmRemove(): Promise<void> {
    const member = this.removing();
    if (member === null) return;
    if (await this.store.removeMember(member.userId)) this.removing.set(null);
  }
}