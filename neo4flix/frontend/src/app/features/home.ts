import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, MovieSearch } from '../core/api.service';
import { Genre, Movie, errorMessage } from '../core/models';
import { MovieCard } from '../shared/movie-card';
import { SearchBar } from './movies/search-bar';

const PAGE_SIZE = 24;

/** Home page: the whole catalogue, searchable. The search lives in the URL, so it can be bookmarked and shared. */
@Component({
  selector: 'app-home',
  imports: [MovieCard, SearchBar],
  template: `
    <div class="container page">
      <div class="page-header">
        <div>
          <h1>Browse movies</h1>
          <p class="muted">{{ total() }} {{ total() === 1 ? 'movie' : 'movies' }} in the catalogue</p>
        </div>
      </div>

      <app-search-bar [genres]="genres()" [initial]="criteria" (search)="onSearch($event)" />

      @if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (loading() && !movies().length) {
        <div class="spinner"></div>
      } @else if (!movies().length) {
        <div class="empty">No movie matches these filters.</div>
      } @else {
        <div class="grid results">
          @for (m of movies(); track m.id) {
            <app-movie-card [movie]="m" />
          }
        </div>
        @if (pages() > 1) {
          <nav class="pager" aria-label="Pages">
            <button class="btn btn-sm" [disabled]="page() === 0" (click)="go(page() - 1)">← Previous</button>
            <span class="muted small">Page {{ page() + 1 }} / {{ pages() }}</span>
            <button class="btn btn-sm" [disabled]="page() + 1 >= pages()" (click)="go(page() + 1)">Next →</button>
          </nav>
        }
      }
    </div>
  `,
  styles: `
    .results { margin-top: 22px; }
    .pager { display: flex; justify-content: center; align-items: center; gap: 16px; margin-top: 28px; }
  `,
})
export class Home {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly movies = signal<Movie[]>([]);
  protected readonly genres = signal<Genre[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected criteria: MovieSearch = {};

  constructor() {
    const q = this.route.snapshot.queryParamMap;
    const num = (k: string) => (q.get(k) ? Number(q.get(k)) : null);
    this.criteria = {
      title: q.get('title') ?? '', genre: q.get('genre') ?? '', sort: q.get('sort') ?? 'title',
      yearFrom: num('yearFrom'), yearTo: num('yearTo'), releasedAfter: q.get('releasedAfter') ?? '',
      releasedBefore: q.get('releasedBefore') ?? '', minRating: num('minRating'),
    };
    this.page.set(num('page') ?? 0);
    this.api.genres().subscribe((g) => this.genres.set(g));
    this.load();
  }

  onSearch(criteria: MovieSearch): void {
    this.criteria = criteria;
    this.page.set(0);
    this.load();
  }

  go(page: number): void {
    this.page.set(page);
    this.load();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const query = { ...this.criteria, page: this.page() || undefined };
    this.router.navigate([], { queryParams: clean(query), replaceUrl: true });
    this.api.searchMovies({ ...this.criteria, page: this.page(), size: PAGE_SIZE }).subscribe({
      next: (result) => {
        this.movies.set(result.items);
        this.total.set(result.total);
        this.pages.set(Math.ceil(result.total / PAGE_SIZE));
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}

function clean(values: object): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(values).filter(([k, v]) => v !== '' && v !== null && v !== undefined && !(k === 'sort' && v === 'title')),
  );
}
