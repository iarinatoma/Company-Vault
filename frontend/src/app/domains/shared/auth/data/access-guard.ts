import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AccessLevel, hasAccess, ResourceType } from '@app/shared/auth/models/access';
import { AccessClient } from './access-client';

/**
 * Protects a route of one resource by access level, e.g. an edit page:
 *
 * ```ts
 * { path: 'vaults/:vaultId/edit', canActivate: [authGuard, accessGuard('VAULT', 'vaultId', 'WRITE')], ... }
 * ```
 *
 * Without the required level — or if the level cannot be loaded — the user is sent to /home.
 * Like hidden buttons this is convenience: the backend checks every request again.
 */
export function accessGuard(
  resourceType: ResourceType,
  idParam: string,
  required: AccessLevel,
): CanActivateFn {
  return (route) => {
    const router = inject(Router);
    const resourceId = route.paramMap.get(idParam);
    if (!resourceId) {
      return router.createUrlTree(['/home']);
    }
    return inject(AccessClient)
      .myLevel(resourceType, resourceId)
      .pipe(
        map((response) => (hasAccess(response.level, required) ? true : router.createUrlTree(['/home']))),
        catchError(() => of(router.createUrlTree(['/home']))),
      );
  };
}