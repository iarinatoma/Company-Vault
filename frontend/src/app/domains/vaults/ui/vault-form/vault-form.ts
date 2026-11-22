import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { User } from '@app/shared/auth/models/auth';
import { CreateVaultInput } from '@app/vaults/models/vault';

interface VaultFormModel {
  name: string;
  description: string;
  ownerId: string;
}

/** Modal dialog to create a general vault. Emits the input; the parent saves. */
@Component({
  selector: 'app-vault-form',
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vault-form.html',
})
export class VaultForm {
  /** The users the admin can choose as owner (the owner gets MANAGE on the new vault). */
  readonly owners = input<readonly User[]>([]);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<CreateVaultInput>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly model = signal<VaultFormModel>({ name: '', description: '', ownerId: '' });

  protected readonly form = form(
    this.model,
    schema<VaultFormModel>((path) => {
      required(path.name);
      maxLength(path.name, 100);
      maxLength(path.description, 500);
      required(path.ownerId);
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
      const { name, description, ownerId } = this.model();
      this.submitted.emit({
        name: name.trim(),
        description: description.trim() === '' ? null : description.trim(),
        ownerId,
      });
    });
  }
}