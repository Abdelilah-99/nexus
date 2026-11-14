import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService, MovieInput } from '../../core/api.service';
import { Movie, errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** Catalogue management (ROLE_ADMIN). The movie-service enforces the role; the adminGuard only hides the page. */
@Component({
  selector: 'app-admin-movies',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="container page">
      <div class="page-header">
        <h1>Manage movies</h1>
        <button class="btn btn-primary" (click)="startCreate()">+ New movie</button>
      </div>

      @if (editing() !== null) {
        <form class="panel form" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="full">{{ editing() ? 'Edit movie' : 'New movie' }}</h2>
          <div class="full"><label for="t">Title</label><input id="t" class="input" formControlName="title" /></div>
          <div><label for="d">Release date</label><input id="d" class="input" type="date" formControlName="releaseDate" /></div>
          <div><label for="r">Runtime (min)</label><input id="r" class="input" type="number" formControlName="runtime" min="1" /></div>
          <div><label for="di">Director</label><input id="di" class="input" formControlName="director" /></div>
          <div><label for="g">Genres (comma separated)</label><input id="g" class="input" formControlName="genres" placeholder="Drama, Sci-Fi" /></div>
          <div class="full"><label for="p">Poster URL (https, optional)</label><input id="p" class="input" formControlName="posterUrl" /></div>
          <div class="full"><label for="o">Overview</label><textarea id="o" class="input" rows="3" formControlName="overview"></textarea></div>
          <div class="full row">
            <button class="btn btn-primary" [disabled]="form.invalid || busy()">Save</button>
            <button type="button" class="btn btn-ghost" (click)="editing.set(null)">Cancel</button>
          </div>
        </form>
      }

      <input class="input search" type="search" [formControl]="query" placeholder="Filter by title…" aria-label="Filter" />
      <div class="panel table-wrap">
        <table>
          <thead><tr><th>Title</th><th>Year</th><th>Genres</th><th>Ratings</th><th></th></tr></thead>
          <tbody>
            @for (m of movies(); track m.id) {
              <tr>
                <td><a [routerLink]="['/movies', m.id]">{{ m.title }}</a></td>
                <td>{{ m.releaseYear }}</td>
                <td class="muted">{{ m.genres.join(', ') }}</td>
                <td>{{ m.ratingCount }}</td>
                <td class="actions">
                  <button class="btn btn-sm" (click)="startEdit(m)">Edit</button>
                  <button class="btn btn-sm btn-ghost btn-danger" (click)="remove(m)">Delete</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `
    .form { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .full { grid-column: 1 / -1; }
    .search { margin-bottom: 12px; max-width: 360px; }
    .table-wrap { overflow-x: auto; padding: 0; }
    table { width: 100%; border-collapse: collapse; font-size: .9rem; }
    th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid var(--border); white-space: nowrap; }
    th { color: var(--text-dim); font-weight: 600; }
    .actions { text-align: right; }
  `,
})
export class AdminMovies {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly movies = signal<Movie[]>([]);
  /** null = form hidden, '' = creating, otherwise the id being edited. */
  protected readonly editing = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly query = new FormControl('');
  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    releaseDate: ['', Validators.required],
    runtime: [null as number | null],
    director: [''],
    genres: ['', Validators.required],
    posterUrl: ['', Validators.pattern(/^(https:\/\/.+)?$/)],
    overview: ['', Validators.maxLength(2000)],
  });

  constructor() {
    this.load();
    this.query.valueChanges.pipe(debounceTime(300)).subscribe(() => this.load());
  }

  startCreate(): void {
    this.form.reset();
    this.editing.set('');
  }

  startEdit(m: Movie): void {
    this.form.reset({
      title: m.title, releaseDate: m.releaseDate ?? '', runtime: m.runtime, director: m.director ?? '',
      genres: m.genres.join(', '), posterUrl: m.posterUrl ?? '', overview: m.overview ?? '',
    });
    this.editing.set(m.id);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  save(): void {
    const v = this.form.getRawValue();
    const body: MovieInput = {
      title: v.title.trim(), releaseDate: v.releaseDate, runtime: v.runtime || null,
      director: v.director.trim() || null, overview: v.overview.trim() || null, posterUrl: v.posterUrl.trim() || null,
      genres: v.genres.split(',').map((g) => g.trim()).filter(Boolean),
    };
    const id = this.editing();
    this.busy.set(true);
    (id ? this.api.updateMovie(id, body) : this.api.createMovie(body)).subscribe({
      next: (m) => {
        this.toast.success(`${m.title} saved`);
        this.editing.set(null);
        this.busy.set(false);
        this.load();
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  remove(m: Movie): void {
    if (!confirm(`Delete “${m.title}”? Its ratings and watchlist entries are deleted too.`)) {
      return;
    }
    this.api.deleteMovie(m.id).subscribe({
      next: () => {
        this.toast.info(`${m.title} deleted`);
        this.movies.update((list) => list.filter((x) => x.id !== m.id));
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  private load(): void {
    this.api.searchMovies({ title: this.query.value ?? '', sort: 'title', size: 100 }).subscribe((p) => this.movies.set(p.items));
  }
}
