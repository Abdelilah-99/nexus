import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, User } from './models';

const STORAGE_KEY = 'neo4flix.session';

interface Session {
  token: string;
  expiresAt: number;
  user: User;
}

/**
 * Holds the JWT and the current user.
 * The session is kept in sessionStorage (cleared when the tab is closed), never in a cookie,
 * and is dropped as soon as the token expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.user()?.roles.includes('ADMIN') ?? false);

  token(): string | null {
    const s = this.session();
    if (s && s.expiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return s?.token ?? null;
  }

  register(username: string, email: string, password: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { username, email, password });
  }

  /** Step 1. Returns mfaRequired=true (and no session) when the account has 2FA enabled. */
  login(login: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { login, password }).pipe(tap((r) => this.store(r)));
  }

  /** Step 2 of a 2FA login: the pending token + the 6-digit code (or a recovery code). */
  verifyTwoFactor(mfaToken: string, code: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/2fa/verify', { mfaToken, code }).pipe(tap((r) => this.store(r)));
  }

  /** Keeps the cached profile in sync after an edit (display name, 2FA switched on...). */
  updateUser(user: User): void {
    const s = this.session();
    if (s) {
      this.persist({ ...s, user });
    }
  }

  logout(redirect = true): void {
    this.clear();
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  private store(response: LoginResponse): void {
    if (response.accessToken && response.user && response.expiresIn) {
      this.persist({
        token: response.accessToken,
        user: response.user,
        expiresAt: Date.now() + response.expiresIn * 1000,
      });
    }
  }

  private persist(session: Session): void {
    this.session.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      /* storage unavailable (private mode): the session still lives in memory */
    }
  }

  private clear(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      const session = raw ? (JSON.parse(raw) as Session) : null;
      return session && session.expiresAt > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
