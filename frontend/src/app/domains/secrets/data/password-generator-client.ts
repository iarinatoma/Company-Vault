import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '@app/shared/config/util/app-config';
import { GeneratedPassword, PasswordOptions } from '@app/secrets/models/generated-password';

/**
 * HTTP layer for `GET /api/v1/password-generator`: the backend generates the password with
 * `SecureRandom` and returns it only in the response body, together with its strength.
 */
@Injectable({ providedIn: 'root' })
export class PasswordGeneratorClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(APP_CONFIG).baseUrl;

  generate(options: PasswordOptions): Observable<GeneratedPassword> {
    const params = new HttpParams()
      .set('length', options.length)
      .set('lowercase', options.lowercase)
      .set('uppercase', options.uppercase)
      .set('digits', options.digits)
      .set('symbols', options.symbols);
    return this.http.get<GeneratedPassword>(`${this.baseUrl}/api/v1/password-generator`, { params });
  }
}
