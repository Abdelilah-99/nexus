import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Pages that need a logged-in user. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.token() ? true : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login / register: no point showing them to someone already logged in. */
export const guestGuard: CanActivateFn = () => {
  return inject(AuthService).token() ? inject(Router).createUrlTree(['/']) : true;
};

/** Admin pages. The backend checks ROLE_ADMIN again: this only hides the UI. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() && auth.isAdmin() ? true : inject(Router).createUrlTree(['/']);
};
