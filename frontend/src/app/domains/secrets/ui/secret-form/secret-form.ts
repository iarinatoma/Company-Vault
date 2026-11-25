import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, required, schema, submit } from '@angular/forms/signals';
import {
  Folder,
  Secret,
  SECRET_TYPES,
  SecretFields,
  SecretInput,
  SecretType,
} from '@app/secrets/models/secret';
import {
  buildSecretFields,
  CustomFieldRow,
  hasNamelessField,
  isEmptyFields,
  MAX_CUSTOM_FIELDS,
  secretTypeLabel,
  secretValueLabel,
  toSecretType,
} from '@app/secrets/util/secret-fields';

interface SecretFormModel {
  name: string;
  type: string;
  value: string;
  username: string;
  url: string;
  folderId: string;
  description: string;
  tagsText: string;
  expiresAtLocal: string;
}

function toDatetimeLocal(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function parseTags(raw: string): string[] {
  const unique = new Set<string>();
  for (const part of raw.split(',')) {
    const cleaned = part.trim().toLowerCase();
    if (cleaned !== '') unique.add(cleaned);
  }
  return [...unique];
}

/**
 * Modal dialog to create (`secret` is null) or edit a secret. Emits the input; the parent saves.
 * The type-specific fields (username, URL, custom fields) are encrypted like the value, so on an
 * edit they can only be changed once the user has shown the secret (`currentFields`); otherwise
 * they are kept as they are.
 */
@Component({
  selector: 'app-secret-form',
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './secret-form.html',
})
export class SecretForm {
  readonly secret = input<Secret | null>(null);
  readonly folders = input<readonly Folder[]>([]);
  readonly defaultFolderId = input<string | null>(null);
  /** The decrypted fields of the edited secret, if the user has shown it; null otherwise. */
  readonly currentFields = input<SecretFields | null>(null);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<SecretInput>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly isCreate = computed(() => this.secret() === null);
  protected readonly types = SECRET_TYPES.map((type) => ({ type, label: secretTypeLabel(type) }));
  protected readonly maxCustomFields = MAX_CUSTOM_FIELDS;

  /** The current value is never pre-filled: editing it means typing a new one. */
  protected readonly model = linkedSignal<SecretFormModel>(() => {
    const current = this.secret();
    const fields = this.currentFields();
    return {
      name: current?.name ?? '',
      type: current ? toSecretType(current.type) : 'LOGIN',
      value: '',
      username: fields?.username ?? '',
      url: fields?.url ?? '',
      folderId: current ? (current.folderId ?? '') : (this.defaultFolderId() ?? ''),
      description: current?.description ?? '',
      tagsText: (current?.tags ?? []).join(', '),
      expiresAtLocal: toDatetimeLocal(current?.expiresAt),
    };
  });

  protected readonly customRows = linkedSignal<CustomFieldRow[]>(() =>
    (this.currentFields()?.customFields ?? []).map((field) => ({
      name: field.name,
      value: field.value,
    })),
  );
  protected readonly customRowsTouched = signal(false);
  protected readonly customRowsInvalid = computed(() => hasNamelessField(this.customRows()));

  protected readonly selectedType = computed<SecretType>(() => toSecretType(this.model().type));
  protected readonly isLogin = computed(() => this.selectedType() === 'LOGIN');
  protected readonly isNote = computed(() => this.selectedType() === 'NOTE');
  protected readonly valueLabel = computed(() => secretValueLabel(this.selectedType()));
  /** New secrets always; an edited secret only after "Show", so no stored field is lost unseen. */
  protected readonly fieldsEditable = computed(
    () => this.isCreate() || this.currentFields() !== null,
  );

  protected readonly form = form(
    this.model,
    schema<SecretFormModel>((path) => {
      required(path.name);
      maxLength(path.name, 200);
      required(path.value, { when: () => this.isCreate() });
      maxLength(path.value, 10000);
      maxLength(path.username, 200);
      maxLength(path.url, 2000);
      maxLength(path.description, 1000);
    }),
  );

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (!el.open) el.showModal();
    });
  }

  protected addCustomField(): void {
    if (this.customRows().length >= MAX_CUSTOM_FIELDS) return;
    this.customRows.update((rows) => [...rows, { name: '', value: '' }]);
  }

  protected removeCustomField(index: number): void {
    this.customRows.update((rows) => rows.filter((_, i) => i !== index));
  }

  protected onCustomFieldInput(index: number, key: keyof CustomFieldRow, event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.customRows.update((rows) =>
      rows.map((row, i) => (i === index ? { ...row, [key]: text } : row)),
    );
  }

  protected async save(): Promise<void> {
    this.customRowsTouched.set(true);
    const rowsValid = !this.customRowsInvalid();
    await submit(this.form, async () => {
      if (!rowsValid) return;
      const { name, value, username, url, folderId, description, tagsText, expiresAtLocal } =
        this.model();
      const type = this.selectedType();
      const trimmedDescription = description.trim();
      const tags = parseTags(tagsText);
      const expiresAt = expiresAtLocal ? new Date(expiresAtLocal).toISOString() : null;

      let fields: SecretFields | null = null;
      if (this.fieldsEditable()) {
        const built = buildSecretFields(type, username, url, this.customRows());
        fields = this.isCreate() && isEmptyFields(built) ? null : built;
      }

      this.submitted.emit({
        name: name.trim(),
        value: value === '' ? null : value,
        folderId: folderId === '' ? null : folderId,
        description: trimmedDescription === '' ? null : trimmedDescription,
        tags,
        expiresAt,
        type,
        fields,
      });
    });
  }
}
