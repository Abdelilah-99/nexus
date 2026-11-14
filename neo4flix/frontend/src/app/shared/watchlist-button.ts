import { Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/models';
import { ToastService } from '../core/toast.service';
import { WatchlistStore } from '../core/watchlist.store';

@Component({
  selector: 'app-watchlist-button',
  template: `
    <button type="button" class="btn" [class.btn-sm]="small()" [class.saved]="saved()" [disabled]="busy()"
            (click)="toggle($event)" [attr.aria-pressed]="saved()">
      {{ saved() ? '✓ In watchlist' : '+ Watchlist' }}
    </button>
  `,
  styles: `.saved { border-color: var(--ok); color: var(--ok); }`,
})
export class WatchlistButton {
  readonly movieId = input.required<string>();
  readonly small = input(false);

  private readonly store = inject(WatchlistStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly busy = signal(false);
  protected readonly saved = computed(() => this.store.ids().has(this.movieId()));

  constructor() {
    if (this.auth.isLoggedIn()) {
      this.store.load();
    }
  }

  toggle(event: Event): void {
    // The button can sit inside a card link: don't open the movie when clicking it.
    event.preventDefault();
    event.stopPropagation();
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.busy.set(true);
    const wasSaved = this.saved();
    this.store.toggle(this.movieId()).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(wasSaved ? 'Removed from your watchlist' : 'Saved to your watchlist');
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(errorMessage(err));
      },
    });
  }
}
