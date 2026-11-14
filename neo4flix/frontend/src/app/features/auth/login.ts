import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/models';

/** Two steps: password first, then the 6-digit code when the account has 2FA enabled. */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="container">
      <div class="panel panel-narrow stack">
        @if (!mfaToken()) {
          <h1>Welcome back</h1>
          @if (expired) {
            <div class="alert alert-info">Your session expired. Please log in again.</div>
          }
          <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
            <div>
              <label for="login">Username or e-mail</label>
              <input id="login" class="input" formControlName="login" autocomplete="username" />
            </div>
            <div>
              <label for="password">Password</label>
              <input id="password" class="input" type="password" formControlName="password" autocomplete="current-password" />
            </div>
            @if (error()) {
              <div class="alert alert-error">{{ error() }}</div>
            }
            <button class="btn btn-primary btn-block" [disabled]="form.invalid || busy()">
              {{ busy() ? 'Signing in…' : 'Log in' }}
            </button>
          </form>
          <p class="muted small">New to Neo4flix? <a routerLink="/register" class="link">Create an account</a></p>
        } @else {
          <h1>Two-factor check</h1>
          <p class="muted">Enter the 6-digit code from your authenticator app, or one of your recovery codes.</p>
          <form [formGroup]="codeForm" (ngSubmit)="verify()" class="stack">
            <input class="input code-input" formControlName="code" inputmode="numeric" autocomplete="one-time-code"
                   placeholder="123 456" aria-label="Authentication code" />
            @if (error()) {
              <div class="alert alert-error">{{ error() }}</div>
            }
            <button class="btn btn-primary btn-block" [disabled]="codeForm.invalid || busy()">Verify</button>
            <button type="button" class="btn btn-ghost btn-sm" (click)="mfaToken.set(null)">Back</button>
          </form>
        }
      </div>
    </div>
  `,
  styles: `.code-input { font-size: 1.6rem; letter-spacing: .3em; text-align: center; }`,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly expired = this.route.snapshot.queryParamMap.has('expired');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mfaToken = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly codeForm = this.fb.nonNullable.group({ code: ['', [Validators.required, Validators.minLength(6)]] });

  submit(): void {
    const { login, password } = this.form.getRawValue();
    this.run(this.auth.login(login, password), (r) => {
      if (r.mfaRequired && r.mfaToken) {
        this.mfaToken.set(r.mfaToken);
        this.codeForm.reset();
      } else {
        this.done();
      }
    });
  }

  verify(): void {
    this.run(this.auth.verifyTwoFactor(this.mfaToken()!, this.codeForm.getRawValue().code.trim()), () => this.done());
  }

  private run<T>(call: import('rxjs').Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: (value) => {
        this.busy.set(false);
        onSuccess(value);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }

  private done(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    // Only follow internal paths, so a crafted link cannot redirect to another site after login.
    this.router.navigateByUrl(returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/');
  }
}
