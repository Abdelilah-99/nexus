import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { WatchlistItem, errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { WatchlistStore } from '../../core/watchlist.store';
import { Poster } from '../../shared/poster';

@Component({
  selector: 'app-watchlist',
  imports: [RouterLink, Poster, DatePipe],
  template: `
    <div class="container page">
      <div class="page-header">
        <div>
          <h1>My watchlist</h1>
          <p class="muted">{{ items().length }} {{ items().length === 1 ? 'movie' : 'movies' }} saved for later</p>
        </div>
      </div>
      @if (loading()) {
        <div class="spinner"></div>
      } @else {
        <div class="grid">
          @for (item of items(); track item.movieId) {
            <div>
              <a [routerLink]="['/movies', item.movieId]">
                <app-poster [id]="item.movieId" [title]="item.title" [year]="item.releaseYear" [url]="item.posterUrl" />
              </a>
              <div class="meta">
                <strong class="title">{{ item.title }}</strong>
                <div class="muted small">{{ item.genres.slice(0, 2).join(' · ') }}</div>
                <div class="muted small">Added {{ item.addedAt | date: 'mediumDate' }}</div>
                <button class="btn btn-sm btn-ghost btn-danger" (click)="remove(item)">Remove</button>
              </div>
            </div>
          } @empty {
            <div class="empty">Your watchlist is empty. Use “+ Watchlist” on any movie to save it here.</div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .meta { padding-top: 8px; }
    .title { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .btn { padding-left: 0; }
  `,
})
export class Watchlist {
  private readonly api = inject(ApiService);
  private readonly store = inject(WatchlistStore);
  private readonly toast = inject(ToastService);
  protected readonly items = signal<WatchlistItem[]>([]);
  protected readonly loading = signal(true);

  constructor() {
    this.api.watchlist().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  remove(item: WatchlistItem): void {
    this.store.toggle(item.movieId).subscribe({
      next: () => {
        this.items.update((list) => list.filter((i) => i.movieId !== item.movieId));
        this.toast.info(`${item.title} removed`);
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }
}
