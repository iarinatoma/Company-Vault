import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { AuthStore } from '@app/shared/auth/data/auth-store';
import { User } from '@app/shared/auth/models/auth';
import { UserInput } from '@app/home/data/users-client';
import { UsersStore } from '@app/home/data/users-store';
import { ConfirmDialog } from '@app/home/ui/confirm-dialog/confirm-dialog';
import { UserForm } from '@app/home/ui/user-form/user-form';

@Component({
  selector: 'app-home',
  imports: [DatePipe, UserForm, ConfirmDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.html',
})
export class Home implements OnInit {
  protected readonly user = inject(AuthStore).user;
  protected readonly store = inject(UsersStore);

  protected readonly canList = computed(() => {
    const role = this.user()?.role;
    return role === 'ADMIN' || role === 'MANAGER';
  });
  protected readonly canWrite = computed(() => this.user()?.role === 'ADMIN');

  protected readonly editing = signal<User | 'new' | null>(null);
  protected readonly editTarget = computed(() => {
    const e = this.editing();
    return e === 'new' ? null : e;
  });
  protected readonly deleting = signal<User | null>(null);

  protected readonly deleteHeading = $localize`:@@home.deleteTitle:Delete user?`;
  protected readonly deleteLabel = $localize`:@@home.confirmDelete:Delete`;
  protected readonly cancelLabel = $localize`:@@userForm.cancel:Cancel`;

  ngOnInit(): void {
    if (this.canList()) void this.store.load(0);
  }

  protected openCreate(): void {
    this.store.clearSaveError();
    this.editing.set('new');
  }

  protected openEdit(target: User): void {
    this.store.clearSaveError();
    this.editing.set(target);
  }

  protected openDelete(target: User): void {
    this.store.clearSaveError();
    this.deleting.set(target);
  }

  protected closeDialogs(): void {
    this.editing.set(null);
    this.deleting.set(null);
  }

  protected isSelf(target: User): boolean {
    return target.id === this.user()?.id;
  }

  protected async save(input: UserInput): Promise<void> {
    const target = this.editing();
    if (target === null) return;
    const ok =
      target === 'new'
        ? await this.store.create(input)
        : await this.store.update(target.id, input);
    if (ok) this.editing.set(null);
  }

  protected async confirmDelete(): Promise<void> {
    const target = this.deleting();
    if (target === null) return;
    if (await this.store.remove(target.id)) this.deleting.set(null);
  }
}
