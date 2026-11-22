import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { UsersClient } from '@app/home/data/users-client';
import { AuthStore } from '@app/shared/auth/data/auth-store';
import { User } from '@app/shared/auth/models/auth';
import { VaultsStore } from '@app/vaults/data/vaults-store';
import { CreateVaultInput } from '@app/vaults/models/vault';
import { VaultForm } from '@app/vaults/ui/vault-form/vault-form';

/** The vaults the signed-in user may see; administrators can also create general vaults. */
@Component({
  selector: 'app-vaults',
  imports: [DatePipe, VaultForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vaults.html',
})
export class Vaults implements OnInit {
  private readonly usersClient = inject(UsersClient);

  protected readonly user = inject(AuthStore).user;
  protected readonly store = inject(VaultsStore);

  protected readonly isAdmin = computed(() => this.user()?.role === 'ADMINISTRATOR');
  protected readonly creating = signal(false);
  protected readonly owners = signal<readonly User[]>([]);

  ngOnInit(): void {
    void this.store.load();
  }

  protected async openCreate(): Promise<void> {
    this.store.clearSaveError();
    this.creating.set(true);
    // Owner candidates: the admin-only user list (first 100 users, by e-mail).
    try {
      const page = await firstValueFrom(this.usersClient.list(0, 100));
      this.owners.set(page.content);
    } catch {
      this.owners.set([]);
    }
  }

  protected async create(input: CreateVaultInput): Promise<void> {
    if (await this.store.create(input)) this.creating.set(false);
  }
}