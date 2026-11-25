import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { SecureClipboard } from '@app/secrets/data/secure-clipboard';
import { RevealedSecret, SecretType } from '@app/secrets/models/secret';
import { secretValueLabel, webUrl } from '@app/secrets/util/secret-fields';

/**
 * The value of one secret in the list: masked until the user shows it, then the value together
 * with its type-specific fields (username and URL of a login, custom fields of any type), each
 * with a "Copy" button that empties the clipboard again shortly after.
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

  private readonly clipboard = inject(SecureClipboard);

  protected readonly isLogin = computed(() => this.type() === 'LOGIN');
  protected readonly valueLabel = computed(() => secretValueLabel(this.type()));

  protected link(url: string): string | null {
    return webUrl(url);
  }

  protected copy(text: string): void {
    void this.clipboard.copy(text);
  }
}
