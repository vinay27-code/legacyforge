import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserSummary,
} from './auth.models';

/**
 * Holds the in-memory access token and current user, plus the API calls
 * for register / login / logout / refresh / whoami.
 *
 * The refresh token is stored server-side as an httpOnly cookie and is
 * never accessible from JavaScript, so it does not appear in this file.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/auth`;

  private readonly accessToken = signal<string | null>(null);
  private readonly currentUser = signal<UserSummary | null>(null);

  readonly isAuthenticated = computed(() => this.accessToken() !== null);
  readonly user = computed(() => this.currentUser());

  getAccessToken(): string | null {
    return this.accessToken();
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, req, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeSession(res)));
  }

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, req, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeSession(res)));
  }

  refresh(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/refresh`, {}, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeSession(res)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(
        tap(() => this.clearSession()),
        catchError(() => {
          this.clearSession();
          return of(void 0);
        }),
      );
  }

  /**
   * On app boot, silently refresh: if the httpOnly refresh cookie is still
   * valid, we get a fresh access token; otherwise we stay logged out.
   */
  bootstrap(): Observable<AuthResponse | null> {
    return this.refresh().pipe(catchError(() => of(null)));
  }

  private storeSession(res: AuthResponse): void {
    this.accessToken.set(res.accessToken);
    this.currentUser.set(res.user);
  }

  private clearSession(): void {
    this.accessToken.set(null);
    this.currentUser.set(null);
  }
}
