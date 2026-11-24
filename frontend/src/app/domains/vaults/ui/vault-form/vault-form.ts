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
import { VaultType } from '@app/vaults/models/vault';

interface VaultFormModel {
  name: string;
  description: string;
  ownerId: string;
}

/** What the form emits; `ownerId` is null when the creator becomes the owner (a project owner). */
export interface VaultFormValue {
  readonly name: string;
  readonly description: string | null;
  readonly ownerId: string | null;
}

/** Modal dialog to create a general or a project vault. Emits the input; the parent saves. */
@Component({
  selector: 'app-vault-form',
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vault-form.html',
})
export class VaultForm {
  readonly kind = input<Extract<VaultType, 'GENERAL' | 'PROJECT'>>('GENERAL');
  /** Administrators choose the owner; a project owner creating a project vault becomes the owner. */
  readonly chooseOwner = input(true);
  /** The users the admin can choose as owner (the owner gets MANAGE on the new vault). */
  readonly owners = input<readonly User[]>([]);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<VaultFormValue>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly model = signal<VaultFormModel>({ name: '', description: '', ownerId: '' });

  protected readonly form = form(
    this.model,
    schema<VaultFormModel>((path) => {
      required(path.name);
      maxLength(path.name, 100);
      maxLength(path.description, 500);
      required(path.ownerId, { when: () => this.chooseOwner() });
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
        ownerId: this.chooseOwner() ? ownerId : null,
      });
    });
  }
}