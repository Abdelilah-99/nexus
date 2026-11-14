import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/guards';

// Every page is lazy-loaded: the browser downloads a page's code only when it is opened.
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home').then((m) => m.Home), title: 'Neo4flix' },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./features/auth/login').then((m) => m.Login), title: 'Log in · Neo4flix' },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./features/auth/register').then((m) => m.Register), title: 'Sign up · Neo4flix' },
  { path: 'movies/:id', loadComponent: () => import('./features/movies/movie-details').then((m) => m.MovieDetails), title: 'Movie · Neo4flix' },
  { path: 'ratings', canActivate: [authGuard], loadComponent: () => import('./features/ratings/my-ratings').then((m) => m.MyRatings), title: 'My ratings · Neo4flix' },
  { path: 'recommendations', canActivate: [authGuard], loadComponent: () => import('./features/recommendations/recommendations').then((m) => m.Recommendations), title: 'For you · Neo4flix' },
  { path: 'watchlist', canActivate: [authGuard], loadComponent: () => import('./features/watchlist/watchlist').then((m) => m.Watchlist), title: 'Watchlist · Neo4flix' },
  { path: 'friends', canActivate: [authGuard], loadComponent: () => import('./features/social/friends').then((m) => m.Friends), title: 'Friends · Neo4flix' },
  { path: 'shared', canActivate: [authGuard], loadComponent: () => import('./features/social/shared').then((m) => m.Shared), title: 'Shared · Neo4flix' },
  { path: 'account', canActivate: [authGuard], loadComponent: () => import('./features/profile/profile').then((m) => m.Profile), title: 'My account · Neo4flix' },
  { path: 'admin/movies', canActivate: [adminGuard], loadComponent: () => import('./features/admin/admin-movies').then((m) => m.AdminMovies), title: 'Manage movies · Neo4flix' },
  { path: '**', redirectTo: '' },
];
