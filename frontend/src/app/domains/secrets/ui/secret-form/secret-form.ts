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
import { form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import { Secret, SecretInput } from '@app/secrets/models/secret';

interface SecretFormModel {
  name: string;
  value: string;
}

/** Modal dialog to create (`secret` is null) or edit a secret. Emits the input; the parent saves. */
@Component({
  selector: 'app-secret-form',
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './secret-form.html',
})
export class SecretForm {
  readonly secret = input<Secret | null>(null);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<SecretInput>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly isCreate = computed(() => this.secret() === null);

  /** The current value is never pre-filled: editing it means typing a new one. */
  protected readonly model = linkedSignal<SecretFormModel>(() => ({
    name: this.secret()?.name ?? '',
    value: '',
  }));

  protected readonly form = form(
    this.model,
    schema<SecretFormModel>((path) => {
      required(path.name);
      maxLength(path.name, 200);
      required(path.value, { when: () => this.isCreate() });
      maxLength(path.value, 10000);
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
      const { name, value } = this.model();
      this.submitted.emit({ name: name.trim(), value: value === '' ? null : value });
    });
  }
}