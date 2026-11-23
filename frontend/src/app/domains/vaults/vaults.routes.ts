import { Routes } from '@angular/router';
import { VaultDetail } from '@app/vaults/feature-vault-detail/vault-detail';
import { Vaults } from '@app/vaults/feature-vaults/vaults';
import { authGuard } from '@app/shared/auth/data/auth-guard';
import { accessGuard } from '@app/shared/auth/data/access-guard';

export const vaultsRoutes: Routes = [
  { path: '', component: Vaults },
  {
    path: ':vaultId',
    canActivate: [authGuard, accessGuard('VAULT', 'vaultId', 'READ')],
    component: VaultDetail,
  },
];