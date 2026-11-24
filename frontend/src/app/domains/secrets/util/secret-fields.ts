import { CustomField, EMPTY_FIELDS, SECRET_TYPES, SecretFields, SecretType } from '@app/secrets/models/secret';

/** Same limit as the backend (`SecretFields.customFields` has at most 20 entries). */
export const MAX_CUSTOM_FIELDS = 20;

/** One editable row of the custom-fields list in the secret form. */
export interface CustomFieldRow {
  name: string;
  value: string;
}

/** A known type, or API_KEY for anything else (e.g. a secret from before secret types existed). */
export function toSecretType(raw: string | null | undefined): SecretType {
  return SECRET_TYPES.find((type) => type === raw) ?? 'API_KEY';
}

/** Display name of a type, e.g. for the badge in the secret list. */
export function secretTypeLabel(type: SecretType): string {
  switch (type) {
    case 'LOGIN':
      return $localize`:@@secretType.login:Login`;
    case 'NOTE':
      return $localize`:@@secretType.note:Note`;
    case 'CUSTOM':
      return $localize`:@@secretType.custom:Custom`;
    default:
      return $localize`:@@secretType.apiKey:API key`;
  }
}

/** What the main value of a type is called: the password of a login, the text of a note, … */
export function secretValueLabel(type: SecretType): string {
  switch (type) {
    case 'LOGIN':
      return $localize`:@@secretValue.password:Password`;
    case 'NOTE':
      return $localize`:@@secretValue.note:Note`;
    case 'CUSTOM':
      return $localize`:@@secretForm.value:Value`;
    default:
      return $localize`:@@secretValue.key:Key or token`;
  }
}

/** The fields of a reveal response; a backend from before secret types sends none. */
export function normalizeFields(raw: Partial<SecretFields> | null | undefined): SecretFields {
  if (!raw) return EMPTY_FIELDS;
  return {
    username: raw.username ?? null,
    url: raw.url ?? null,
    customFields: raw.customFields ?? [],
  };
}

/** A row with a value but without a name cannot be saved. */
export function hasNamelessField(rows: readonly CustomFieldRow[]): boolean {
  return rows.some((row) => row.name.trim() === '' && row.value !== '');
}

/**
 * The type-specific fields to save. Username and URL belong to a login only; rows without a name
 * (and so without a value) are left out.
 */
export function buildSecretFields(
  type: SecretType,
  username: string,
  url: string,
  rows: readonly CustomFieldRow[],
): SecretFields {
  const login = type === 'LOGIN';
  const customFields: CustomField[] = rows
    .filter((row) => row.name.trim() !== '')
    .map((row) => ({ name: row.name.trim(), value: row.value }));
  return {
    username: login && username.trim() !== '' ? username.trim() : null,
    url: login && url.trim() !== '' ? url.trim() : null,
    customFields,
  };
}

export function isEmptyFields(fields: SecretFields): boolean {
  return fields.username === null && fields.url === null && fields.customFields.length === 0;
}

/** Only http(s) URLs become links; anything else is shown as plain text. */
export function webUrl(url: string): string | null {
  return /^https?:\/\//i.test(url) ? url : null;
}
