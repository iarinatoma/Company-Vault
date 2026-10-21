import { Routes } from '@angular/router';
import { authGuard, guestGuard } from '@app/shared/auth/data/auth-guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadChildren: () => import('@app/account/account.routes').then((m) => m.accountRoutes),
  },
  {
    path: 'home',
    canActivate: [authGuard],
    loadChildren: () => import('@app/home/home.routes').then((m) => m.homeRoutes),
  },
  { path: '**', redirectTo: 'home' },
];
