import { Routes } from '@angular/router';
import { VaultDetail } from '@app/vaults/feature-vault-detail/vault-detail';
import { Vaults } from '@app/vaults/feature-vaults/vaults';

export const vaultsRoutes: Routes = [
  { path: '', component: Vaults },
  { path: ':vaultId', component: VaultDetail },
];