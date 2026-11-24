import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';

/** Downloads the current master key backup. The backend restricts this endpoint to admins. */
@Injectable({ providedIn: 'root' })
export class MasterKeyBackupClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).baseUrl}/api/v1/admin/master-key/backup`;

  download(): Observable<Blob> {
    return this.http.get(this.url, { responseType: 'blob' });
  }
}
