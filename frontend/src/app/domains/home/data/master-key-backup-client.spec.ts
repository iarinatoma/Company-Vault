import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestBackend } from '@app-testing/test-backend';
import { MasterKeyBackupClient } from './master-key-backup-client';

describe('MasterKeyBackupClient', () => {
  let client: MasterKeyBackupClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestBackend() });
    client = TestBed.inject(MasterKeyBackupClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('downloads the admin backup endpoint as a blob', async () => {
    const backup = new Blob(['base64-key']);
    const download = firstValueFrom(client.download());
    const request = http.expectOne('/api/v1/admin/master-key/backup');

    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(backup);

    await expect(download).resolves.toEqual(backup);
  });
});
