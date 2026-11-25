import { EMPTY_FIELDS } from '@app/secrets/models/secret';
import {
  buildSecretFields,
  hasNamelessField,
  isEmptyFields,
  normalizeFields,
  secretTypeLabel,
  secretValueLabel,
  toSecretType,
  webUrl,
} from './secret-fields';

describe('secret fields', () => {
  it('knows the four secret types and treats anything else as an API key', () => {
    expect(toSecretType('LOGIN')).toBe('LOGIN');
    expect(toSecretType('NOTE')).toBe('NOTE');
    expect(toSecretType('CUSTOM')).toBe('CUSTOM');
    expect(toSecretType('API_KEY')).toBe('API_KEY');
    expect(toSecretType('SOMETHING_ELSE')).toBe('API_KEY');
    expect(toSecretType(undefined)).toBe('API_KEY');
  });

  it('names the type and what its main value is', () => {
    expect(secretTypeLabel('LOGIN')).toBe('Login');
    expect(secretTypeLabel('API_KEY')).toBe('API key');
    expect(secretValueLabel('LOGIN')).toBe('Password');
    expect(secretValueLabel('NOTE')).toBe('Note');
    expect(secretValueLabel('CUSTOM')).toBe('Value');
  });

  it('keeps username and URL for a login, trimmed', () => {
    const fields = buildSecretFields('LOGIN', '  alice ', ' https://mail.example.com ', []);

    expect(fields).toEqual({ username: 'alice', url: 'https://mail.example.com', customFields: [] });
  });

  it('drops username and URL for every other type but keeps custom fields', () => {
    const fields = buildSecretFields('API_KEY', 'alice', 'https://x.example', [
      { name: ' Region ', value: 'eu-central-1' },
    ]);

    expect(fields).toEqual({
      username: null,
      url: null,
      customFields: [{ name: 'Region', value: 'eu-central-1' }],
    });
  });

  it('leaves out empty custom-field rows and recognises empty fields', () => {
    const fields = buildSecretFields('NOTE', '', '', [{ name: '', value: '' }]);

    expect(fields).toEqual(EMPTY_FIELDS);
    expect(isEmptyFields(fields)).toBe(true);
    expect(isEmptyFields({ ...EMPTY_FIELDS, username: 'alice' })).toBe(false);
  });

  it('rejects a custom field that has a value but no name', () => {
    expect(hasNamelessField([{ name: '  ', value: 'secret' }])).toBe(true);
    expect(hasNamelessField([{ name: 'PIN', value: '1234' }, { name: '', value: '' }])).toBe(false);
  });

  it('fills in fields that an older backend does not send', () => {
    expect(normalizeFields(undefined)).toEqual(EMPTY_FIELDS);
    expect(normalizeFields(null)).toEqual(EMPTY_FIELDS);
    expect(normalizeFields({ username: 'alice' })).toEqual({
      username: 'alice',
      url: null,
      customFields: [],
    });
  });

  it('links only http and https URLs', () => {
    expect(webUrl('https://mail.example.com')).toBe('https://mail.example.com');
    expect(webUrl('HTTP://intranet.local')).toBe('HTTP://intranet.local');
    expect(webUrl('javascript:alert(1)')).toBeNull();
    expect(webUrl('mail.example.com')).toBeNull();
  });
});
