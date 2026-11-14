import { Routes } from '@angular/router';
import { guestGuard } from './core/guards';

// Every page is lazy-loaded: the browser downloads a page's code only when it is opened.
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home').then((m) => m.Home), title: 'Neo4flix' },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./features/auth/login').then((m) => m.Login), title: 'Log in · Neo4flix' },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./features/auth/register').then((m) => m.Register), title: 'Sign up · Neo4flix' },
  { path: '**', redirectTo: '' },
];
