import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MasterKeyBackupClient } from '@app/home/data/master-key-backup-client';

@Component({
  selector: 'app-master-key-backup',
  templateUrl: './master-key-backup.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MasterKeyBackup {
  private readonly client = inject(MasterKeyBackupClient);

  protected readonly downloading = signal(false);
  protected readonly error = signal(false);

  protected async download(): Promise<void> {
    if (this.downloading()) return;

    this.downloading.set(true);
    this.error.set(false);
    try {
      const backup = await firstValueFrom(this.client.download());
      const url = URL.createObjectURL(backup);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'company-vault-master-key.txt';
      document.body.append(link);
      try {
        link.click();
      } finally {
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 0);
      }
    } catch {
      this.error.set(true);
    } finally {
      this.downloading.set(false);
    }
  }
}
