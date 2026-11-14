import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Rating, errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Poster } from '../../shared/poster';
import { Stars } from '../../shared/stars';

/** "My ratings": every movie I rated, with inline score change and delete. */
@Component({
  selector: 'app-my-ratings',
  imports: [RouterLink, Stars, Poster, DatePipe],
  template: `
    <div class="container page">
      <div class="page-header">
        <div>
          <h1>My ratings</h1>
          <p class="muted">{{ ratings().length }} rated · average {{ average() }}★ · the more you rate, the better your recommendations</p>
        </div>
        <select class="input sort" [value]="sort()" (change)="sort.set($any($event.target).value)" aria-label="Sort">
          <option value="recent">Most recent</option>
          <option value="best">Highest score</option>
          <option value="worst">Lowest score</option>
          <option value="title">Title</option>
        </select>
      </div>
      @if (loading()) {
        <div class="spinner"></div>
      } @else {
        <div class="list">
          @for (r of sorted(); track r.movieId) {
            <article class="item panel">
              <a [routerLink]="['/movies', r.movieId]" class="thumb">
                <app-poster [id]="r.movieId" [title]="r.movieTitle" [year]="r.movieYear" />
              </a>
              <div class="stack body">
                <div>
                  <a [routerLink]="['/movies', r.movieId]"><strong>{{ r.movieTitle }}</strong></a>
                  <span class="muted"> ({{ r.movieYear }})</span>
                  <div class="muted small">Rated {{ r.updatedAt | date: 'mediumDate' }}</div>
                </div>
                <app-stars [editable]="true" [value]="r.score" (valueChange)="change(r, $event)" />
                @if (r.review) { <p class="review">“{{ r.review }}”</p> }
              </div>
              <button class="btn btn-ghost btn-danger btn-sm" (click)="remove(r)" aria-label="Delete rating">✕</button>
            </article>
          } @empty {
            <div class="empty">You have not rated any movie yet. <a class="link" routerLink="/">Browse the catalogue</a>.</div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .sort { width: auto; }
    .list { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 460px), 1fr)); }
    .item { display: grid; grid-template-columns: 72px 1fr auto; gap: 14px; padding: 12px; align-items: start; }
    .body { gap: 6px; }
    .review { margin: 0; color: var(--text-dim); font-style: italic; }
  `,
})
export class MyRatings {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  protected readonly ratings = signal<Rating[]>([]);
  protected readonly loading = signal(true);
  protected readonly sort = signal('recent');

  protected readonly average = computed(() => {
    const list = this.ratings();
    return list.length ? (list.reduce((sum, r) => sum + r.score, 0) / list.length).toFixed(1) : '–';
  });

  protected readonly sorted = computed(() => {
    const list = [...this.ratings()];
    switch (this.sort()) {
      case 'best': return list.sort((a, b) => b.score - a.score);
      case 'worst': return list.sort((a, b) => a.score - b.score);
      case 'title': return list.sort((a, b) => a.movieTitle.localeCompare(b.movieTitle));
      default: return list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    }
  });

  constructor() {
    this.api.myRatings().subscribe({
      next: (r) => {
        this.ratings.set(r);
        this.loading.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  change(rating: Rating, score: number | null): void {
    if (!score || score === rating.score) {
      return;
    }
    this.api.rate(rating.movieId, score, rating.review).subscribe({
      next: (updated) => {
        this.ratings.update((list) => list.map((r) => (r.movieId === updated.movieId ? updated : r)));
        this.toast.success(`${rating.movieTitle}: ${score}★`);
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  remove(rating: Rating): void {
    this.api.deleteRating(rating.movieId).subscribe({
      next: () => {
        this.ratings.update((list) => list.filter((r) => r.movieId !== rating.movieId));
        this.toast.info(`Rating for ${rating.movieTitle} deleted`);
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }
}
