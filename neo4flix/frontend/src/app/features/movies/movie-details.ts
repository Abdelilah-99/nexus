import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Movie, MovieRatings, SimilarMovie, errorMessage } from '../../core/models';
import { MovieCard } from '../../shared/movie-card';
import { Poster } from '../../shared/poster';
import { Stars } from '../../shared/stars';

/** /movies/:id: information, average rating with its distribution, latest reviews and similar movies. */
@Component({
  selector: 'app-movie-details',
  imports: [Poster, Stars, MovieCard, DatePipe, DecimalPipe],
  template: `
    <div class="container page">
      @if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (!movie()) {
        <div class="spinner"></div>
      } @else {
        @let m = movie()!;
        <section class="hero">
          <app-poster class="poster" [id]="m.id" [title]="m.title" [year]="m.releaseYear" [url]="m.posterUrl" />
          <div class="info stack">
            <div>
              <h1>{{ m.title }} <span class="muted year">({{ m.releaseYear }})</span></h1>
              <div class="row">
                @for (g of m.genres; track g) { <span class="chip">{{ g }}</span> }
              </div>
            </div>
            <dl class="facts">
              <div><dt>Release date</dt><dd>{{ m.releaseDate | date: 'longDate' }}</dd></div>
              @if (m.runtime) { <div><dt>Runtime</dt><dd>{{ m.runtime }} min</dd></div> }
              @if (m.director) { <div><dt>Director</dt><dd>{{ m.director }}</dd></div> }
              <div>
                <dt>Average rating</dt>
                <dd class="avg">
                  @if (m.averageRating !== null) {
                    <app-stars [value]="m.averageRating" /> <strong>{{ m.averageRating | number: '1.1-1' }}</strong>
                    <span class="muted">/ 5 · {{ m.ratingCount }} {{ m.ratingCount === 1 ? 'rating' : 'ratings' }}</span>
                  } @else {
                    <span class="muted">Not rated yet</span>
                  }
                </dd>
              </div>
            </dl>
            @if (m.overview) { <p class="overview">{{ m.overview }}</p> }
          </div>
        </section>

        @if (ratings(); as r) {
          <section class="panel two-cols">
            <div>
              <h2>Ratings</h2>
              @for (entry of distribution(); track entry.star) {
                <div class="dist-row">
                  <span>{{ entry.star }} ★</span>
                  <div class="bar"><span [style.width.%]="entry.percent"></span></div>
                  <span class="muted small">{{ entry.count }}</span>
                </div>
              }
            </div>
            <div>
              <h2>Latest reviews</h2>
              @for (review of r.ratings; track review.userId) {
                <article class="review">
                  <div class="row"><strong>{{ review.username }}</strong><app-stars [value]="review.score" />
                    <span class="muted small">{{ review.updatedAt | date: 'mediumDate' }}</span></div>
                  @if (review.review) { <p>{{ review.review }}</p> }
                </article>
              } @empty {
                <p class="muted">No reviews yet. Be the first!</p>
              }
            </div>
          </section>
        }

        @if (similar().length) {
          <section class="similar">
            <h2>More like this</h2>
            <div class="grid">
              @for (s of similar(); track s.movie.id) {
                <app-movie-card [movie]="s.movie"><div class="reason">{{ s.reasons[0] }}</div></app-movie-card>
              }
            </div>
          </section>
        }
      }
    </div>
  `,
  styles: `
    .hero { display: grid; grid-template-columns: 260px 1fr; gap: 32px; margin-bottom: 28px; }
    .year { font-weight: 400; }
    .facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin: 0; }
    dt { font-size: .78rem; color: var(--text-dim); text-transform: uppercase; letter-spacing: .05em; }
    dd { margin: 2px 0 0; }
    .avg { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .overview { font-size: 1.05rem; color: #cfd5e2; max-width: 70ch; }
    .two-cols { display: grid; grid-template-columns: 320px 1fr; gap: 32px; margin-bottom: 28px; }
    .dist-row { display: grid; grid-template-columns: 36px 1fr 30px; gap: 8px; align-items: center; margin: 6px 0; font-size: .9rem; }
    .bar { height: 8px; border-radius: 4px; background: var(--bg-elev-2); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--gold); }
    .review { padding: 10px 0; border-bottom: 1px solid var(--border); }
    .review p { margin: 6px 0 0; color: #cfd5e2; }
    .reason { font-size: .75rem; color: var(--accent-2); margin-top: 2px; }
    @media (max-width: 760px) {
      .hero, .two-cols { grid-template-columns: 1fr; }
      .poster { max-width: 220px; }
    }
  `,
})
export class MovieDetails {
  /** Bound from the :id route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly title = inject(Title);

  protected readonly movie = signal<Movie | null>(null);
  protected readonly ratings = signal<MovieRatings | null>(null);
  protected readonly similar = signal<SimilarMovie[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly distribution = computed(() => {
    const r = this.ratings();
    if (!r) {
      return [];
    }
    return [5, 4, 3, 2, 1].map((star) => {
      const count = r.distribution[String(star)] ?? 0;
      return { star, count, percent: r.ratingCount ? (count / r.ratingCount) * 100 : 0 };
    });
  });

  constructor() {
    // Re-runs when the id changes (clicking a similar movie reuses this component).
    effect(() => this.load(this.id()));
  }

  protected load(id: string): void {
    this.movie.set(null);
    this.error.set(null);
    this.api.movie(id).subscribe({
      next: (m) => {
        this.movie.set(m);
        this.title.setTitle(`${m.title} · Neo4flix`);
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
    this.reloadRatings(id);
    this.api.similar(id).subscribe((s) => this.similar.set(s));
  }

  protected reloadRatings(id: string): void {
    this.api.movieRatings(id).subscribe((r) => this.ratings.set(r));
  }
}
