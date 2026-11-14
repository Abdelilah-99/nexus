import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { passwordRules, passwordScore } from '../../shared/password-policy';

function samePasswords(group: AbstractControl): ValidationErrors | null {
  return group.get('password')?.value === group.get('confirm')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="container">
      <div class="panel panel-narrow stack">
        <h1>Create your account</h1>
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
          <div>
            <label for="username">Username</label>
            <input id="username" class="input" formControlName="username" autocomplete="username" />
            @if (form.controls.username.touched && form.controls.username.invalid) {
              <div class="field-error">3–30 characters: letters, digits, ".", "_" or "-".</div>
            }
          </div>
          <div>
            <label for="email">E-mail</label>
            <input id="email" class="input" type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <div class="field-error">Enter a valid e-mail address.</div>
            }
          </div>
          <div>
            <label for="password">Password</label>
            <input id="password" class="input" type="password" formControlName="password" autocomplete="new-password" />
            <div class="meter" aria-hidden="true"><span [style.width.%]="score() * 100" [class]="strength()"></span></div>
            <ul class="rules">
              @for (rule of rules(); track rule.label) {
                <li [class.ok]="rule.ok">{{ rule.ok ? '✓' : '•' }} {{ rule.label }}</li>
              }
            </ul>
          </div>
          <div>
            <label for="confirm">Confirm password</label>
            <input id="confirm" class="input" type="password" formControlName="confirm" autocomplete="new-password" />
            @if (form.controls.confirm.touched && form.hasError('mismatch')) {
              <div class="field-error">Passwords do not match.</div>
            }
          </div>
          @if (error()) {
            <div class="alert alert-error">{{ error() }}</div>
          }
          <button class="btn btn-primary btn-block" [disabled]="form.invalid || score() < 1 || busy()">
            {{ busy() ? 'Creating…' : 'Sign up' }}
          </button>
        </form>
        <p class="muted small">Already have an account? <a routerLink="/login" class="link">Log in</a></p>
      </div>
    </div>
  `,
  styles: `
    .rules { list-style: none; padding: 0; margin: 8px 0 0; display: grid; grid-template-columns: 1fr 1fr; gap: 2px 12px; font-size: .8rem; color: var(--text-dim); }
    .rules li.ok { color: var(--ok); }
    .meter { height: 4px; background: var(--border); border-radius: 2px; margin-top: 8px; overflow: hidden; }
    .meter span { display: block; height: 100%; transition: width .2s; }
    .weak { background: var(--danger); } .medium { background: var(--warn); } .strong { background: var(--ok); }
    @media (max-width: 420px) { .rules { grid-template-columns: 1fr; } }
  `,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group(
    {
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(30), Validators.pattern(/^[a-zA-Z0-9_.-]+$/)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required],
      confirm: ['', Validators.required],
    },
    { validators: samePasswords },
  );

  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly rules = computed(() => passwordRules(this.values().password ?? '', this.values().username ?? ''));
  protected readonly score = computed(() => passwordScore(this.rules()));
  protected readonly strength = computed(() => (this.score() < 0.5 ? 'weak' : this.score() < 1 ? 'medium' : 'strong'));

  submit(): void {
    const { username, email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth
      .register(username, email, password)
      .pipe(switchMap(() => this.auth.login(username, password)))
      .subscribe({
        next: () => {
          this.toast.success(`Welcome to Neo4flix, ${username}!`);
          this.router.navigateByUrl('/');
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(errorMessage(err));
        },
      });
  }
}
