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
import { Folder, Secret, SecretInput } from '@app/secrets/models/secret';

interface SecretFormModel {
  name: string;
  value: string;
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

/** Modal dialog to create (`secret` is null) or edit a secret. Emits the input; the parent saves. */
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
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly submitted = output<SecretInput>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly isCreate = computed(() => this.secret() === null);

  /** The current value is never pre-filled: editing it means typing a new one. */
  protected readonly model = linkedSignal<SecretFormModel>(() => {
    const current = this.secret();
    return {
      name: current?.name ?? '',
      value: '',
      folderId: current ? (current.folderId ?? '') : (this.defaultFolderId() ?? ''),
      description: current?.description ?? '',
      tagsText: (current?.tags ?? []).join(', '),
      expiresAtLocal: toDatetimeLocal(current?.expiresAt),
    };
  });

  protected readonly form = form(
    this.model,
    schema<SecretFormModel>((path) => {
      required(path.name);
      maxLength(path.name, 200);
      required(path.value, { when: () => this.isCreate() });
      maxLength(path.value, 10000);
      maxLength(path.description, 1000);
    }),
  );

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (!el.open) el.showModal();
    });
  }

  protected async save(): Promise<void> {
    await submit(this.form, async () => {
      const { name, value, folderId, description, tagsText, expiresAtLocal } = this.model();
      const trimmedDescription = description.trim();
      const tags = parseTags(tagsText);
      const expiresAt = expiresAtLocal ? new Date(expiresAtLocal).toISOString() : null;

      this.submitted.emit({
        name: name.trim(),
        value: value === '' ? null : value,
        folderId: folderId === '' ? null : folderId,
        description: trimmedDescription === '' ? null : trimmedDescription,
        tags,
        expiresAt,
      });
    });
  }
}
