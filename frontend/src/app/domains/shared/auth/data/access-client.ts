import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AccessLevelResponse, ResourceType } from '@app/shared/auth/models/access';
import { APP_CONFIG } from '@app/shared/config/util/app-config';

/** Thin HTTP layer for `/api/v1/access` — the caller's own level on a resource. */
@Injectable({ providedIn: 'root' })
export class AccessClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(APP_CONFIG).baseUrl;

  myLevel(resourceType: ResourceType, resourceId: string): Observable<AccessLevelResponse> {
    return this.http.get<AccessLevelResponse>(
      `${this.baseUrl}/api/v1/access/${resourceType}/${encodeURIComponent(resourceId)}/me`,
    );
  }
}