import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

/** Ids of the movies in my watchlist, shared by every "Save" button so they all stay in sync. */
@Injectable({ providedIn: 'root' })
export class WatchlistStore {
  private readonly api = inject(ApiService);
  readonly ids = signal<ReadonlySet<string>>(new Set());
  private loaded = false;

  constructor() {
    // Another account in the same tab must never see the previous user's watchlist.
    const auth = inject(AuthService);
    effect(() => {
      const userId = auth.user()?.id;
      untracked(() => {
        this.reset();
        if (userId) {
          this.load();
        }
      });
    });
  }

  load(force = false): void {
    if (this.loaded && !force) {
      return;
    }
    this.loaded = true;
    this.api.watchlist().subscribe({
      next: (items) => this.ids.set(new Set(items.map((i) => i.movieId))),
      error: () => (this.loaded = false),
    });
  }

  has(movieId: string): boolean {
    return this.ids().has(movieId);
  }

  toggle(movieId: string): Observable<void> {
    const adding = !this.has(movieId);
    const call = adding ? this.api.addToWatchlist(movieId) : this.api.removeFromWatchlist(movieId);
    return call.pipe(
      tap(() =>
        this.ids.update((set) => {
          const next = new Set(set);
          if (adding) {
            next.add(movieId);
          } else {
            next.delete(movieId);
          }
          return next;
        }),
      ),
    );
  }

  reset(): void {
    this.loaded = false;
    this.ids.set(new Set());
  }
}
