import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toDataURL } from 'qrcode';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';

type Step = 'idle' | 'scan' | 'codes';

/** Turn TOTP two-factor authentication on (QR code → first code → recovery codes) or off. */
@Component({
  selector: 'app-two-factor',
  imports: [FormsModule],
  template: `
    <section class="panel stack">
      <div class="row">
        <h2>Two-factor authentication</h2>
        <span class="spacer"></span>
        <span class="chip" [class.on]="enabled()">{{ enabled() ? 'Enabled' : 'Disabled' }}</span>
      </div>

      @switch (step()) {
        @case ('scan') {
          <p class="muted">1. Scan this QR code with Google Authenticator, Authy or Microsoft Authenticator.</p>
          <div class="qr-row">
            <img [src]="qr()" alt="QR code for your authenticator app" width="180" height="180" />
            <div class="stack">
              <p class="muted small">Can't scan? Enter this key manually:</p>
              <code class="secret">{{ secret() }}</code>
            </div>
          </div>
          <p class="muted">2. Enter the 6-digit code shown in the app.</p>
          <div class="row">
            <input class="input code" [ngModel]="code()" (ngModelChange)="code.set($event)" inputmode="numeric" maxlength="6" placeholder="123456" aria-label="Code" />
            <button class="btn btn-primary" [disabled]="code().length !== 6 || busy()" (click)="enable()">Activate</button>
            <button class="btn btn-ghost" (click)="step.set('idle')">Cancel</button>
          </div>
        }
        @case ('codes') {
          <div class="alert alert-ok">Two-factor authentication is on.</div>
          <p><strong>Save these recovery codes now.</strong> Each one works once if you lose your phone. They will not be shown again.</p>
          <div class="codes">
            @for (c of recoveryCodes(); track c) { <code>{{ c }}</code> }
          </div>
          <div class="row">
            <button class="btn" (click)="copyCodes()">Copy codes</button>
            <button class="btn btn-primary" (click)="step.set('idle')">I saved them</button>
          </div>
        }
        @default {
          @if (enabled()) {
            <p class="muted">Each login asks for a code from your authenticator app. To turn it off, confirm with your password and a current code.</p>
            <div class="row">
              <input class="input grow" type="password" [ngModel]="password()" (ngModelChange)="password.set($event)" placeholder="Password" aria-label="Password" autocomplete="current-password" />
              <input class="input code" [ngModel]="code()" (ngModelChange)="code.set($event)" placeholder="Code" aria-label="Code" />
              <button class="btn btn-danger" [disabled]="!password() || !code() || busy()" (click)="disable()">Turn off</button>
            </div>
          } @else {
            <p class="muted">Protect your account with a second step: even with your password, nobody can log in without your phone.</p>
            <div><button class="btn btn-primary" [disabled]="busy()" (click)="setup()">Set up 2FA</button></div>
          }
        }
      }
    </section>
  `,
  styles: `
    .chip.on { border-color: var(--ok); color: var(--ok); }
    .qr-row { display: flex; gap: 20px; align-items: center; flex-wrap: wrap; }
    .qr-row img { background: #fff; padding: 8px; border-radius: 10px; }
    .secret { word-break: break-all; font-size: .95rem; background: var(--bg-elev-2); padding: 8px 10px; border-radius: 8px; }
    .code { width: 130px; letter-spacing: .2em; }
    .grow { flex: 1; min-width: 160px; }
    .codes { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 8px; }
    .codes code { background: var(--bg-elev-2); padding: 8px; border-radius: 8px; text-align: center; }
  `,
})
export class TwoFactor {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly enabled = signal(this.auth.user()?.twoFactorEnabled ?? false);
  protected readonly step = signal<Step>('idle');
  protected readonly qr = signal('');
  protected readonly secret = signal('');
  protected readonly recoveryCodes = signal<string[]>([]);
  protected readonly busy = signal(false);
  // Signals, not plain fields: Angular 22 components are OnPush by default, so a plain field changed
  // inside an HTTP callback would not refresh the template.
  protected readonly code = signal('');
  protected readonly password = signal('');

  setup(): void {
    this.busy.set(true);
    this.api.setupTwoFactor().subscribe({
      next: async (r) => {
        // The QR code is drawn in the browser: the secret is never sent to a third-party QR service.
        this.qr.set(await toDataURL(r.otpauthUri, { width: 180, margin: 1 }));
        this.secret.set(r.secret);
        this.code.set('');
        this.step.set('scan');
        this.busy.set(false);
      },
      error: (err) => this.fail(err),
    });
  }

  enable(): void {
    this.busy.set(true);
    this.api.enableTwoFactor(this.code()).subscribe({
      next: (r) => {
        this.recoveryCodes.set(r.recoveryCodes);
        this.secret.set('');
        this.qr.set('');
        this.code.set('');
        this.enabled.set(true);
        this.step.set('codes');
        this.busy.set(false);
        this.syncUser();
      },
      error: (err) => this.fail(err),
    });
  }

  disable(): void {
    this.busy.set(true);
    this.api.disableTwoFactor(this.password(), this.code()).subscribe({
      next: () => {
        this.enabled.set(false);
        this.password.set('');
        this.code.set('');
        this.busy.set(false);
        this.toast.info('Two-factor authentication turned off');
        this.syncUser();
      },
      error: (err) => this.fail(err),
    });
  }

  async copyCodes(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.recoveryCodes().join('\n'));
      this.toast.success('Recovery codes copied');
    } catch {
      this.toast.error('Copy failed: write them down instead');
    }
  }

  private syncUser(): void {
    this.api.me().subscribe((u) => this.auth.updateUser(u));
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.toast.error(errorMessage(err));
  }
}
