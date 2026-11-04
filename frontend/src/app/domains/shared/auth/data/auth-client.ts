import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import {
  LoginCredentials,
  LoginResponse,
  RegisterCredentials,
  TokenPair,
  User,
} from '@app/shared/auth/models/auth';

/**
 * Thin HTTP layer for `/api/v1/auth` and `/api/v1/users/me`. Cookie-backed auth calls include
 * credentials so the HttpOnly refresh token can be sent and rotated.
 */
@Injectable({ providedIn: 'root' })
export class AuthClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(APP_CONFIG).baseUrl;

  login(credentials: LoginCredentials): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/api/v1/auth/login`, credentials, {
      withCredentials: true,
    });
  }

  register(credentials: RegisterCredentials): Observable<User> {
    return this.http.post<User>(`${this.baseUrl}/api/v1/auth/register`, credentials);
  }

  refresh(): Observable<TokenPair> {
    return this.http.post<TokenPair>(`${this.baseUrl}/api/v1/auth/refresh`, null, {
      withCredentials: true,
    });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/logout`, null, {
      withCredentials: true,
    });
  }

  me(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/api/v1/users/me`);
  }
}
