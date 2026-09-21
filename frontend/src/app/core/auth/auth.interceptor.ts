import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

/**
 * - Adds Authorization header when we have an access token.
 * - On 401, tries to silently refresh once and replays the request.
 * - If refresh fails, clears the session and pushes user to /login.
 *
 * A shared subject guards against dozens of concurrent 401s all trying
 * to refresh at once.
 */

let isRefreshing = false;
const refreshedToken$ = new BehaviorSubject<string | null>(null);
const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const isApiCall = req.url.startsWith(environment.apiBaseUrl);
  const isAuthEndpoint = AUTH_PATHS.some((p) => req.url.includes(p));

  // Always allow credentials on API calls (so httpOnly refresh cookie flows).
  const requestWithCreds = isApiCall
    ? req.clone({ withCredentials: true })
    : req;

  const token = auth.getAccessToken();
  const authed =
    token && isApiCall && !isAuthEndpoint
      ? requestWithCreds.clone({
          setHeaders: { Authorization: `Bearer ${token}` },
        })
      : requestWithCreds;

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (
        err.status !== 401 ||
        isAuthEndpoint ||
        !isApiCall
      ) {
        return throwError(() => err);
      }

      // If we don't even have a token, no point trying to refresh.
      if (!auth.getAccessToken() && !document.cookie) {
        router.navigate(['/login']);
        return throwError(() => err);
      }

      if (!isRefreshing) {
        isRefreshing = true;
        refreshedToken$.next(null);

        return auth.refresh().pipe(
          switchMap((res) => {
            isRefreshing = false;
            refreshedToken$.next(res.accessToken);
            const retried = authed.clone({
              setHeaders: { Authorization: `Bearer ${res.accessToken}` },
            });
            return next(retried);
          }),
          catchError((refreshErr) => {
            isRefreshing = false;
            refreshedToken$.next(null);
            auth.logout().subscribe();
            router.navigate(['/login']);
            return throwError(() => refreshErr);
          }),
        );
      }

      // A refresh is already in flight; wait for it and then retry.
      return refreshedToken$.pipe(
        filter((t): t is string => t !== null),
        take(1),
        switchMap((t) => {
          const retried = authed.clone({
            setHeaders: { Authorization: `Bearer ${t}` },
          });
          return next(retried);
        }),
      );
    }),
  );
};
