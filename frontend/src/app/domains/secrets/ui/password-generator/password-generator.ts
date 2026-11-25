import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PasswordGeneratorClient } from '@app/secrets/data/password-generator-client';
import {
  CharacterSet,
  DEFAULT_PASSWORD_OPTIONS,
  GeneratedPassword,
  GENERATOR_MAX_LENGTH,
  GENERATOR_MIN_LENGTH,
  PasswordOptions,
} from '@app/secrets/models/generated-password';
import {
  clampLength,
  strengthColor,
  strengthLabel,
  strengthLevel,
} from '@app/secrets/util/password-strength';

/**
 * Password generator inside the secret form (spec 4.3): length, character sets and a strength
 * meter. The backend generates the password; every generated password is emitted so the form can
 * put it into the value field.
 */
@Component({
  selector: 'app-password-generator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './password-generator.html',
})
export class PasswordGenerator {
  readonly generated = output<string>();

  private readonly client = inject(PasswordGeneratorClient);

  protected readonly minLength = GENERATOR_MIN_LENGTH;
  protected readonly maxLength = GENERATOR_MAX_LENGTH;
  protected readonly levels = [1, 2, 3, 4];
  protected readonly characterSets: readonly { key: CharacterSet; label: string }[] = [
    { key: 'lowercase', label: 'a–z' },
    { key: 'uppercase', label: 'A–Z' },
    { key: 'digits', label: '0–9' },
    { key: 'symbols', label: $localize`:@@generator.symbols:Symbols (!@#…)` },
  ];

  protected readonly options = signal<PasswordOptions>(DEFAULT_PASSWORD_OPTIONS);
  protected readonly result = signal<GeneratedPassword | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly anySetChosen = computed(() => {
    const { lowercase, uppercase, digits, symbols } = this.options();
    return lowercase || uppercase || digits || symbols;
  });
  protected readonly strength = computed(() => {
    const result = this.result();
    if (!result) return null;
    return {
      level: strengthLevel(result.strength),
      label: strengthLabel(result.strength),
      color: strengthColor(result.strength),
      bits: result.entropyBits,
    };
  });

  protected onLengthInput(event: Event): void {
    const length = clampLength(Number((event.target as HTMLInputElement).value));
    this.options.update((options) => ({ ...options, length }));
  }

  protected onSetChange(key: CharacterSet, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.options.update((options) => ({ ...options, [key]: checked }));
  }

  protected async generate(): Promise<void> {
    if (!this.anySetChosen() || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      const result = await firstValueFrom(this.client.generate(this.options()));
      this.result.set(result);
      this.generated.emit(result.password);
    } catch {
      this.error.set($localize`:@@generator.error:The password could not be generated. Please try again.`);
    } finally {
      this.busy.set(false);
    }
  }
}
