/** Shape of the backend's `SecretResponse`: metadata only, never the value (masked by default). */
export interface Secret {
  readonly id: string;
  readonly vaultId: string;
  readonly name: string;
  readonly ownerEmail: string | null;
  readonly updatedByEmail: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Create needs a value; on edit an empty or missing value keeps the current one. */
export interface SecretInput {
  readonly name: string;
  readonly value: string | null;
}