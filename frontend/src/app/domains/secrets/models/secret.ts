/** The kind of secret (spec 4.3); plain metadata, so the list can show it without decrypting. */
export type SecretType = 'LOGIN' | 'API_KEY' | 'NOTE' | 'CUSTOM';

export const SECRET_TYPES: readonly SecretType[] = ['LOGIN', 'API_KEY', 'NOTE', 'CUSTOM'];

/** One custom key-value field ("eigene Felder"); any secret type can have up to 20. */
export interface CustomField {
  readonly name: string;
  readonly value: string;
}

/** Type-specific fields: username and URL of a login, custom fields of any type. Stored encrypted. */
export interface SecretFields {
  readonly username: string | null;
  readonly url: string | null;
  readonly customFields: readonly CustomField[];
}

export const EMPTY_FIELDS: SecretFields = { username: null, url: null, customFields: [] };

/** What an explicit reveal returns: the decrypted value and the decrypted type-specific fields. */
export interface RevealedSecret {
  readonly value: string;
  readonly fields: SecretFields;
}

/** Shape of the backend's `SecretResponse`: metadata only, never the value (masked by default). */
export interface Secret {
  readonly id: string;
  readonly vaultId: string;
  readonly folderId: string | null;
  readonly name: string;
  readonly type: SecretType;
  readonly description: string | null;
  readonly tags: readonly string[];
  readonly expiresAt: string | null;
  readonly ownerEmail: string | null;
  readonly updatedByEmail: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Create needs a value; on edit an empty or missing value keeps the current one. A missing `type`
 * means API_KEY on create and "unchanged" on edit; missing `fields` keep the stored fields.
 */
export interface SecretInput {
  readonly name: string;
  readonly value: string | null;
  readonly folderId?: string | null;
  readonly description?: string | null;
  readonly tags?: readonly string[] | null;
  readonly expiresAt?: string | null;
  readonly type?: SecretType;
  readonly fields?: SecretFields | null;
}

export interface MoveSecretInput {
  readonly targetVaultId: string | null;
  readonly targetFolderId: string | null;
}

export interface SecretFilter {
  readonly q?: string;
  readonly tag?: string;
  readonly folderId?: string;
  readonly rootOnly?: boolean;
}

export interface Folder {
  readonly id: string;
  readonly vaultId: string;
  readonly name: string;
  readonly secretCount: number;
  readonly createdByEmail: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface FolderInput {
  readonly name: string;
}
