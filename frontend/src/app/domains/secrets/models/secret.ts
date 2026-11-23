/** Shape of the backend's `SecretResponse`: metadata only, never the value (masked by default). */
export interface Secret {
  readonly id: string;
  readonly vaultId: string;
  readonly folderId: string | null;
  readonly name: string;
  readonly description: string | null;
  readonly tags: readonly string[];
  readonly expiresAt: string | null;
  readonly ownerEmail: string | null;
  readonly updatedByEmail: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Create needs a value; on edit an empty or missing value keeps the current one. */
export interface SecretInput {
  readonly name: string;
  readonly value: string | null;
  readonly folderId?: string | null;
  readonly description?: string | null;
  readonly tags?: readonly string[] | null;
  readonly expiresAt?: string | null;
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
