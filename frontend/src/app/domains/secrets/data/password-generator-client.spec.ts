import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { PasswordGeneratorClient } from './password-generator-client';

describe('PasswordGeneratorClient', () => {
  let client: PasswordGeneratorClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    client = TestBed.inject(PasswordGeneratorClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the backend for a password with the chosen length and character sets', async () => {
    const generated = firstValueFrom(
      client.generate({ length: 24, lowercase: true, uppercase: false, digits: true, symbols: false }),
    );
    const request = http.expectOne((req) => req.url === '/api/v1/password-generator');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('length')).toBe('24');
    expect(request.request.params.get('lowercase')).toBe('true');
    expect(request.request.params.get('uppercase')).toBe('false');
    expect(request.request.params.get('digits')).toBe('true');
    expect(request.request.params.get('symbols')).toBe('false');
    request.flush({ password: 'k3p9x2m7q1w8e4r6t0y5u3i2', entropyBits: 124, strength: 'VERY_STRONG' });

    expect(await generated).toEqual({
      password: 'k3p9x2m7q1w8e4r6t0y5u3i2',
      entropyBits: 124,
      strength: 'VERY_STRONG',
    });
  });
});
