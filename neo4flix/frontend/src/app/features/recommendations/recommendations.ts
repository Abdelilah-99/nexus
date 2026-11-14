import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Genre, Recommendation, errorMessage } from '../../core/models';
import { Poster } from '../../shared/poster';
import { WatchlistButton } from '../../shared/watchlist-button';

const STRATEGY_LABELS: Record<string, string> = {
  hybrid: 'For you',
  collaborative: 'Similar members',
  content: 'Your genres',
  popular: 'Popular',
};

/** Personalised recommendations from the recommendation-service, filterable by genre and release date. */
@Component({
  selector: 'app-recommendations',
  imports: [ReactiveFormsModule, RouterLink, Poster, WatchlistButton, DecimalPipe],
  template: `
    <div class="container page">
      <div class="page-header">
        <div>
          <h1>Recommended for you</h1>
          <p class="muted">Computed live from the Neo4j graph: your ratings, members with similar taste and the genres you like.</p>
        </div>
      </div>

      <form [formGroup]="filters" class="panel filters">
        <div>
          <label for="strategy">Algorithm</label>
          <select id="strategy" class="input" formControlName="strategy">
            <option value="hybrid">Hybrid (recommended)</option>
            <option value="collaborative">Members like me</option>
            <option value="content">My favourite genres</option>
            <option value="popular">Most popular</option>
          </select>
        </div>
        <div>
          <label for="genre">Genre</label>
          <select id="genre" class="input" formControlName="genre">
            <option value="">All genres</option>
            @for (g of genres(); track g.name) { <option [value]="g.name">{{ g.name }}</option> }
          </select>
        </div>
        <div><label for="yf">Year from</label><input id="yf" class="input" type="number" formControlName="yearFrom" min="1900" max="2100" /></div>
        <div><label for="yt">Year to</label><input id="yt" class="input" type="number" formControlName="yearTo" min="1900" max="2100" /></div>
        <div><label for="ra">Released after</label><input id="ra" class="input" type="date" formControlName="releasedAfter" /></div>
        <div><label for="rb">Released before</label><input id="rb" class="input" type="date" formControlName="releasedBefore" /></div>
      </form>

      @if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (loading()) {
        <div class="spinner"></div>
      } @else {
        @if (coldStart()) {
          <div class="alert alert-info">Rate a few movies to get personalised picks. Meanwhile, here is what other members love.</div>
        }
        <div class="list">
          @for (r of recommendations(); track r.movie.id; let i = $index) {
            <article class="rec panel">
              <a [routerLink]="['/movies', r.movie.id]" class="thumb">
                <app-poster [id]="r.movie.id" [title]="r.movie.title" [year]="r.movie.releaseYear" [url]="r.movie.posterUrl" />
              </a>
              <div class="body">
                <div class="row top">
                  <span class="rank">#{{ i + 1 }}</span>
                  <span class="chip" [class]="'chip s-' + r.strategy">{{ label(r.strategy) }}</span>
                  <span class="match">{{ r.score * 100 | number: '1.0-0' }}% match</span>
                </div>
                <a [routerLink]="['/movies', r.movie.id]"><h3>{{ r.movie.title }} <span class="muted">({{ r.movie.releaseYear }})</span></h3></a>
                <div class="muted small">
                  {{ r.movie.genres.join(' · ') }}
                  @if (r.movie.averageRating !== null) { · ★ {{ r.movie.averageRating | number: '1.1-1' }} }
                </div>
                <ul class="reasons">
                  @for (reason of r.reasons; track reason) { <li>{{ reason }}</li> }
                </ul>
                <div class="row">
                  <app-watchlist-button [movieId]="r.movie.id" [small]="true" />
                </div>
              </div>
            </article>
          } @empty {
            <div class="empty">No recommendation matches these filters. Try a wider date range or another genre.</div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .list { display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 520px), 1fr)); }
    .rec { display: grid; grid-template-columns: 110px 1fr; gap: 16px; padding: 14px; }
    .body { min-width: 0; display: flex; flex-direction: column; gap: 6px; }
    .top { gap: 8px; }
    .rank { font-weight: 800; color: var(--text-dim); }
    .match { margin-left: auto; color: var(--ok); font-weight: 700; font-size: .85rem; }
    h3 { margin: 0; font-size: 1.1rem; }
    .reasons { margin: 0; padding-left: 18px; font-size: .85rem; color: #cfd5e2; }
    .s-hybrid { background: var(--accent); border-color: var(--accent); color: #fff; }
    .s-collaborative { border-color: #6c5ce7; color: #b9b0ff; }
    .s-content { border-color: #00b894; color: #7be3c6; }
  `,
})
export class Recommendations {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly recommendations = signal<Recommendation[]>([]);
  protected readonly genres = signal<Genre[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly coldStart = signal(false);

  protected readonly filters = this.fb.group({
    strategy: ['hybrid'],
    genre: [''],
    yearFrom: [null as number | null],
    yearTo: [null as number | null],
    releasedAfter: [''],
    releasedBefore: [''],
  });

  constructor() {
    this.api.genres().subscribe((g) => this.genres.set(g));
    this.filters.valueChanges.pipe(debounceTime(300)).subscribe(() => this.load());
    this.load();
  }

  label(strategy: string): string {
    return STRATEGY_LABELS[strategy] ?? strategy;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const f = this.filters.getRawValue();
    this.api
      .recommendations({
        strategy: f.strategy ?? 'hybrid', genre: f.genre ?? '', yearFrom: f.yearFrom, yearTo: f.yearTo,
        releasedAfter: f.releasedAfter ?? '', releasedBefore: f.releasedBefore ?? '', limit: 24,
      })
      .subscribe({
        next: (list) => {
          this.recommendations.set(list);
          this.coldStart.set(f.strategy === 'hybrid' && list.length > 0 && list.every((r) => r.strategy === 'popular'));
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err));
          this.loading.set(false);
        },
      });
  }
}
