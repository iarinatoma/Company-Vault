import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { CreateProjectVaultInput, CreateVaultInput, Vault, VaultMember } from '@app/vaults/models/vault';
import { AccessLevel } from '@app/shared/auth/models/access';

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

  
  createProject(input: CreateProjectVaultInput): Observable<Vault> {
    return this.http.post<Vault>(`${this.url}/projects`, input);
  }

  members(vaultId: string): Observable<VaultMember[]> {
    return this.http.get<VaultMember[]>(`${this.url}/${encodeURIComponent(vaultId)}/members`);
  }

  addMember(vaultId: string, email: string, accessLevel: AccessLevel): Observable<VaultMember> {
    return this.http.post<VaultMember>(`${this.url}/${encodeURIComponent(vaultId)}/members`, {
      email,
      accessLevel,
    });
  }

  updateMember(vaultId: string, userId: string, accessLevel: AccessLevel): Observable<VaultMember> {
    return this.http.put<VaultMember>(
      `${this.url}/${encodeURIComponent(vaultId)}/members/${encodeURIComponent(userId)}`,
      { accessLevel },
    );
  }

  removeMember(vaultId: string, userId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.url}/${encodeURIComponent(vaultId)}/members/${encodeURIComponent(userId)}`,
    );
  }

  update(id: string, name: string, description: string | null): Observable<Vault> {
    return this.http.put<Vault>(`${this.url}/${encodeURIComponent(id)}`, { name, description });
  }

  archive(id: string): Observable<Vault> {
    return this.http.put<Vault>(`${this.url}/${encodeURIComponent(id)}/archive`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`);
  }
}