import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { Secret, SecretInput } from '@app/secrets/models/secret';

/** Thin HTTP layer for `/api/v1/vaults/{vaultId}/secrets`; the backend checks the access level on the vault. */
@Injectable({ providedIn: 'root' })
export class SecretsClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(APP_CONFIG).baseUrl;

  private url(vaultId: string): string {
    return `${this.baseUrl}/api/v1/vaults/${encodeURIComponent(vaultId)}/secrets`;
  }

  list(vaultId: string): Observable<Secret[]> {
    return this.http.get<Secret[]>(this.url(vaultId));
  }

  /** The decrypted value, fetched only on an explicit "Show". */
  reveal(vaultId: string, secretId: string): Observable<string> {
    return this.http
      .get<{ value: string }>(`${this.url(vaultId)}/${encodeURIComponent(secretId)}/value`)
      .pipe(map((response) => response.value));
  }

  create(vaultId: string, input: SecretInput): Observable<Secret> {
    return this.http.post<Secret>(this.url(vaultId), input);
  }

  update(vaultId: string, secretId: string, input: SecretInput): Observable<Secret> {
    return this.http.put<Secret>(`${this.url(vaultId)}/${encodeURIComponent(secretId)}`, input);
  }

  delete(vaultId: string, secretId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(vaultId)}/${encodeURIComponent(secretId)}`);
  }
}