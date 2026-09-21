import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'repos',
        loadComponent: () =>
          import('./features/repos/repo-list.component').then((m) => m.RepoListComponent),
      },
      {
        path: 'repos/new',
        loadComponent: () =>
          import('./features/repos/repo-wizard.component').then((m) => m.RepoWizardComponent),
      },
      {
        path: 'repos/:id',
        loadComponent: () =>
          import('./features/repos/repo-detail.component').then((m) => m.RepoDetailComponent),
      },
      { path: '', pathMatch: 'full', redirectTo: 'repos' },
    ],
  },
  { path: '**', redirectTo: '' },
];
