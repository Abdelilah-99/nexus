import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Adds "Authorization: Bearer <JWT>" to every call to our own API (never to another origin),
 * and sends the user back to the login page when the token is rejected.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const ownApi = req.url.startsWith('/api/');
  const request = token && ownApi ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      const isLoginCall = req.url.startsWith('/api/auth/');
      if (err.status === 401 && token && !isLoginCall) {
        auth.logout(false);
        router.navigate(['/login'], { queryParams: { expired: 1, returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};
