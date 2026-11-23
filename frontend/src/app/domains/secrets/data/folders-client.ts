import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { Folder, FolderInput } from '@app/secrets/models/secret';

/** Thin HTTP layer for `/api/v1/vaults/{vaultId}/folders`. */
@Injectable({ providedIn: 'root' })
export class FoldersClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(APP_CONFIG).baseUrl;

  private url(vaultId: string): string {
    return `${this.baseUrl}/api/v1/vaults/${encodeURIComponent(vaultId)}/folders`;
  }

  list(vaultId: string): Observable<Folder[]> {
    return this.http.get<Folder[]>(this.url(vaultId));
  }

  create(vaultId: string, input: FolderInput): Observable<Folder> {
    return this.http.post<Folder>(this.url(vaultId), input);
  }

  update(vaultId: string, folderId: string, input: FolderInput): Observable<Folder> {
    return this.http.put<Folder>(`${this.url(vaultId)}/${encodeURIComponent(folderId)}`, input);
  }

  delete(vaultId: string, folderId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(vaultId)}/${encodeURIComponent(folderId)}`);
  }
}
