import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersClient } from '@app/home/data/users-client';
import { AuthStore } from '@app/shared/auth/data/auth-store';
import { User } from '@app/shared/auth/models/auth';
import { VaultsStore } from '@app/vaults/data/vaults-store';
import { VaultForm, VaultFormValue } from '@app/vaults/ui/vault-form/vault-form';
import { VaultType } from '@app/vaults/models/vault';

/** The vaults the signed-in user may see; `?type=PROJECT` (the "Projects" menu) shows project vaults only. */
@Component({
  selector: 'app-vaults',
  imports: [DatePipe, RouterLink, RouterLinkActive, VaultForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vaults.html',
})
export class Vaults implements OnInit {
  /** Query parameter `type` (component input binding). */
  readonly type = input<string | undefined>();

  private readonly usersClient = inject(UsersClient);

  protected readonly user = inject(AuthStore).user;
  protected readonly store = inject(VaultsStore);

  protected readonly isAdmin = computed(() => this.user()?.role === 'ADMINISTRATOR');
  protected readonly canCreateProject = computed(() => {
    const role = this.user()?.role;
    return role === 'ADMINISTRATOR' || role === 'PROJECT_OWNER';
  });
  protected readonly tabs: readonly { label: string; type: VaultType | null }[] = [
    { label: $localize`:@@vaults.tabAll:All`, type: null },
    { label: $localize`:@@vaults.tabGeneral:General`, type: 'GENERAL' },
    { label: $localize`:@@vaults.tabProject:Projects`, type: 'PROJECT' },
    { label: $localize`:@@vaults.tabPersonal:Personal`, type: 'PERSONAL' },
  ];
  protected readonly search = signal('');
  protected readonly visibleVaults = computed(() => {
    const type = this.type();
    const term = this.search().trim().toLowerCase();
    return this.store
      .items()
      .filter((vault) => !type || vault.type === type)
      .filter((vault) => term === '' || vault.name.toLowerCase().includes(term));
  });

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected readonly creating = signal<'GENERAL' | 'PROJECT' | null>(null);
  protected readonly owners = signal<readonly User[]>([]);

  ngOnInit(): void {
    void this.store.load();
  }

  protected async openCreate(kind: 'GENERAL' | 'PROJECT'): Promise<void> {
    this.store.clearSaveError();
    this.creating.set(kind);
    if (!this.isAdmin()) return;
    // Owner candidates: the admin-only user list (first 100 users, by e-mail).
    try {
      const page = await firstValueFrom(this.usersClient.list(0, 100));
      this.owners.set(page.content);
    } catch {
      this.owners.set([]);
    }
  }

  protected async create(value: VaultFormValue): Promise<void> {
    const kind = this.creating();
    if (kind === null) return;
    const ok =
      kind === 'PROJECT'
        ? await this.store.createProject(value)
        : await this.store.create({ ...value, ownerId: value.ownerId ?? '' });
    if (ok) this.creating.set(null);
  }
}