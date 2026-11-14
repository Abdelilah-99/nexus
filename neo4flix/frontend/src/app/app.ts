import { Component, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/auth.service';
import { SharesStore } from './core/shares.store';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly toasts = inject(ToastService);
  protected readonly shares = inject(SharesStore);
  protected menuOpen = false;

  constructor() {
    // Load the badge as soon as someone logs in (and clear it on logout)...
    effect(() => {
      const loggedIn = this.auth.isLoggedIn();
      untracked(() => (loggedIn ? this.shares.refresh() : this.shares.unread.set(0)));
    });
    // ...and refresh the "Shared" badge on every page change while logged in.
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed()).subscribe(() => {
      if (this.auth.isLoggedIn()) {
        this.shares.refresh();
      }
    });
  }
}
