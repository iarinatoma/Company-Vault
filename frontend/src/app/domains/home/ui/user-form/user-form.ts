import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  linkedSignal,
  output,
  viewChild,
} from '@angular/core';
import {
  applyWhen,
  disabled,
  email,
  form,
  FormField,
  minLength,
  required,
  schema,
  submit,
} from '@angular/forms/signals';
import { ROLES, Role, User } from '@app/shared/auth/models/auth';
import { UserInput } from '@app/home/data/users-client';

interface UserFormModel {
  email: string;
  role: Role;
  password: string;
}

/** Modal dialog to create (`user` is null) or edit a user. Emits the input; the parent saves. */
@Component({
  selector: 'app-user-form',
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-form.html',
})
export class UserForm {
  readonly user = input<User | null>(null);
  /** True when the edited user is the signed-in one (the backend forbids changing own role). */
  readonly isSelf = input(false);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<UserInput>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly roles = ROLES;
  protected readonly isCreate = computed(() => this.user() === null);

  protected readonly model = linkedSignal<UserFormModel>(() => ({
    email: this.user()?.email ?? '',
    role: this.user()?.role ?? 'USER',
    password: '',
  }));

  protected readonly form = form(
    this.model,
    schema<UserFormModel>((path) => {
      required(path.email);
      // The backend rejects a role change on one's own account.
      disabled(path.role, () => this.isSelf());
      email(path.email);
      // A new user needs a password; when editing, empty means "keep the current one".
      required(path.password, { when: () => this.isCreate() });
      applyWhen(
        path.password,
        ({ value }) => value() !== '',
        (password) => minLength(password, 12),
      );
    }),
  );

  constructor() {
    // Native modal: focus trap, Escape and backdrop come from the browser.
    effect(() => {
      const el = this.dialog().nativeElement;
      if (!el.open) el.showModal();
    });
  }

  protected async save(): Promise<void> {
    await submit(this.form, async () => {
      const { email, role, password } = this.model();
      this.submitted.emit(password ? { email, role, password } : { email, role });
    });
  }
}
