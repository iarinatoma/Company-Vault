import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { CreateVaultInput, Vault } from '@app/vaults/models/vault';

/** Thin HTTP layer for `/api/v1/vaults` (creating is ADMIN-only; the backend decides what you see). */
@Injectable({ providedIn: 'root' })
export class VaultsClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).baseUrl}/api/v1/vaults`;

  list(): Observable<Vault[]> {
    return this.http.get<Vault[]>(this.url);
  }

  get(id: string): Observable<Vault> {
    return this.http.get<Vault>(`${this.url}/${encodeURIComponent(id)}`);
  }

  create(input: CreateVaultInput): Observable<Vault> {
    return this.http.post<Vault>(this.url, input);
  }
}