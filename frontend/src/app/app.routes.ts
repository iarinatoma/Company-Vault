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
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('@app/account/feature-register/register').then((m) => m.Register),
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('@app/account/feature-forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('@app/account/feature-reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'home',
    canActivate: [authGuard],
    loadChildren: () => import('@app/home/home.routes').then((m) => m.homeRoutes),
  },
  { path: '**', redirectTo: 'home' },
];
