import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { Role, User } from '@app/shared/auth/models/auth';

export interface PageInfo {
  readonly size: number;
  readonly number: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

/** Shape of the backend's `PagedModel<UserResponse>`. */
export interface UserPage {
  readonly content: readonly User[];
  readonly page: PageInfo;
}

/** Create and update share one shape; on update an absent password keeps the current one. */
export interface UserInput {
  readonly email: string;
  readonly role: Role;
  readonly password?: string;
}

/** Thin HTTP layer for `/api/v1/users` (list, create, update and delete are ADMIN-only writes). */
@Injectable({ providedIn: 'root' })
export class UsersClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).baseUrl}/api/v1/users`;

  list(page: number, size: number): Observable<UserPage> {
    return this.http.get<UserPage>(this.url, { params: { page, size, sort: 'email,asc' } });
  }

  create(input: UserInput): Observable<User> {
    return this.http.post<User>(this.url, input);
  }

  update(id: string, input: UserInput): Observable<User> {
    return this.http.put<User>(`${this.url}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
