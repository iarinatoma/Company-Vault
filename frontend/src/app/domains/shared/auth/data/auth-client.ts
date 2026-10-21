import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { LoginCredentials, LoginResponse, TokenPair, User } from '@app/shared/auth/models/auth';

/**
 * Thin HTTP layer for `/api/v1/auth` and `/api/v1/users/me`. The refresh token lives in an
 * HttpOnly cookie, so every auth call is sent with credentials.
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
