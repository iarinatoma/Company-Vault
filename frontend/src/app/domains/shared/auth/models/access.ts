/** Access levels on a vault, project, folder or secret — same order as the backend AccessLevel. */
export type AccessLevel = 'NO_ACCESS' | 'READ' | 'WRITE' | 'MANAGE';

/** The kinds of resource an access level can be granted on — same names as the backend ResourceType. */
export type ResourceType = 'VAULT' | 'PROJECT' | 'FOLDER' | 'SECRET';

/** From least to most; every level includes all lower ones. */
export const ACCESS_LEVELS: readonly AccessLevel[] = ['NO_ACCESS', 'READ', 'WRITE', 'MANAGE'];

/** Answer of `GET /api/v1/access/{type}/{id}/me`. */
export interface AccessLevelResponse {
  readonly resourceType: ResourceType;
  readonly resourceId: string;
  readonly level: AccessLevel;
}

/**
 * True if `level` allows everything `required` allows — e.g. `hasAccess('MANAGE', 'WRITE')`.
 * Use it to show or hide buttons: READ → view/copy, WRITE → edit, MANAGE → share/delete.
 * Hiding a button is convenience only; the backend checks every request again.
 */
export function hasAccess(level: AccessLevel | null | undefined, required: AccessLevel): boolean {
  if (!level) return false;
  return ACCESS_LEVELS.indexOf(level) >= ACCESS_LEVELS.indexOf(required);
}