import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { passwordRules, passwordScore } from '../../shared/password-policy';
import { TwoFactor } from './two-factor';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, DatePipe, TwoFactor],
  template: `
    <div class="container page">
      <h1>My account</h1>
      @if (auth.user(); as u) {
        <p class="muted">&#64;{{ u.username }} · member since {{ u.createdAt | date: 'longDate' }}
          @if (auth.isAdmin()) { · <span class="chip">ADMIN</span> }</p>
      }
      <div class="cols">
        <section class="panel">
          <h2>Profile</h2>
          <form [formGroup]="profile" (ngSubmit)="saveProfile()" class="stack">
            <div><label for="dn">Display name</label><input id="dn" class="input" formControlName="displayName" maxlength="50" /></div>
            <div><label for="em">E-mail</label><input id="em" class="input" type="email" formControlName="email" /></div>
            <div><label for="bio">Bio</label><textarea id="bio" class="input" rows="3" formControlName="bio" maxlength="500"></textarea></div>
            <div><button class="btn btn-primary" [disabled]="profile.invalid || profile.pristine">Save profile</button></div>
          </form>
        </section>

        <section class="panel">
          <h2>Change password</h2>
          <form [formGroup]="passwords" (ngSubmit)="changePassword()" class="stack">
            <!-- Lets password managers know which account this password belongs to. -->
            <input type="text" autocomplete="username" [value]="auth.user()?.username ?? ''" hidden readonly />
            <div><label for="cur">Current password</label><input id="cur" class="input" type="password" formControlName="current" autocomplete="current-password" /></div>
            <div>
              <label for="new">New password</label>
              <input id="new" class="input" type="password" formControlName="next" autocomplete="new-password" />
              <ul class="rules">
                @for (rule of rules(); track rule.label) { <li [class.ok]="rule.ok">{{ rule.ok ? '✓' : '•' }} {{ rule.label }}</li> }
              </ul>
            </div>
            <div><button class="btn btn-primary" [disabled]="passwords.invalid || score() < 1">Change password</button></div>
          </form>
        </section>
      </div>
      <app-two-factor />
    </div>
  `,
  styles: `
    .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 16px 0 20px; }
    .rules { list-style: none; padding: 0; margin: 8px 0 0; font-size: .8rem; color: var(--text-dim); columns: 2; }
    .rules li.ok { color: var(--ok); }
    @media (max-width: 760px) { .cols { grid-template-columns: 1fr; } .rules { columns: 1; } }
  `,
})
export class Profile {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly profile = this.fb.nonNullable.group({
    displayName: [this.auth.user()?.displayName ?? '', Validators.maxLength(50)],
    email: [this.auth.user()?.email ?? '', [Validators.required, Validators.email]],
    bio: [this.auth.user()?.bio ?? '', Validators.maxLength(500)],
  });

  protected readonly passwords = this.fb.nonNullable.group({
    current: ['', Validators.required],
    next: ['', Validators.required],
  });
  private readonly newPassword = toSignal(this.passwords.controls.next.valueChanges, { initialValue: '' });
  protected readonly rules = computed(() => passwordRules(this.newPassword(), this.auth.user()?.username ?? ''));
  protected readonly score = computed(() => passwordScore(this.rules()));
  protected readonly busy = signal(false);

  constructor() {
    // Refresh from the server in case the profile changed elsewhere.
    this.api.me().subscribe((u) => {
      this.auth.updateUser(u);
      this.profile.reset({ displayName: u.displayName, email: u.email, bio: u.bio ?? '' });
    });
  }

  saveProfile(): void {
    this.api.updateMe(this.profile.getRawValue()).subscribe({
      next: (u) => {
        this.auth.updateUser(u);
        this.profile.reset({ displayName: u.displayName, email: u.email, bio: u.bio ?? '' });
        this.toast.success('Profile saved');
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  changePassword(): void {
    const { current, next } = this.passwords.getRawValue();
    this.api.changePassword(current, next).subscribe({
      next: () => {
        this.passwords.reset();
        this.toast.success('Password changed');
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }
}
