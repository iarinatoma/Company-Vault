import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RevealedSecret, SecretType } from '@app/secrets/models/secret';
import { secretValueLabel, webUrl } from '@app/secrets/util/secret-fields';

/**
 * The value of one secret in the list: masked until the user shows it, then the value together
 * with its type-specific fields (username and URL of a login, custom fields of any type).
 */
@Component({
  selector: 'app-secret-value',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './secret-value.html',
})
export class SecretValue {
  readonly type = input.required<SecretType>();
  /** Null while masked; set only after an explicit "Show". */
  readonly revealed = input<RevealedSecret | null>(null);

  protected readonly isLogin = computed(() => this.type() === 'LOGIN');
  protected readonly valueLabel = computed(() => secretValueLabel(this.type()));

  protected link(url: string): string | null {
    return webUrl(url);
  }
}
