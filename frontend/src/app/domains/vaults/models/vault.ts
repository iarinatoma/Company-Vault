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


/** A new project vault: a project owner leaves `ownerId` empty (they become the owner); an admin chooses one. */
export interface CreateProjectVaultInput {
  readonly name: string;
  readonly description: string | null;
  readonly ownerId: string | null;
}

/** One row of "who has access to this vault?". */
export interface VaultMember {
  readonly userId: string;
  readonly email: string;
  readonly accessLevel: AccessLevel;
}