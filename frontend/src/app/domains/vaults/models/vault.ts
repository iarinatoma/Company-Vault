import { AccessLevel } from '@app/shared/auth/models/access';

export type VaultType = 'GENERAL' | 'PERSONAL' | 'PROJECT';

/** Shape of the backend's `VaultResponse`: metadata plus the caller's own access level. */
export interface Vault {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly type: VaultType;
  readonly archived: boolean;
  readonly createdAt: string;
  readonly accessLevel: AccessLevel;
}

/** A new general vault; the owner gets MANAGE, the creating admin gets no automatic access. */
export interface CreateVaultInput {
  readonly name: string;
  readonly description: string | null;
  readonly ownerId: string;
}