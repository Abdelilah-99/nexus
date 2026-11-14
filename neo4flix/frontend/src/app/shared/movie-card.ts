import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Movie } from '../core/models';
import { Poster } from './poster';

@Component({
  selector: 'app-movie-card',
  imports: [RouterLink, Poster, DecimalPipe],
  template: `
    <a class="card" [routerLink]="['/movies', movie().id]">
      <app-poster [id]="movie().id" [title]="movie().title" [year]="movie().releaseYear" [url]="movie().posterUrl" />
      <div class="meta">
        <div class="title">{{ movie().title }}</div>
        <div class="sub">
          <span>{{ movie().releaseYear }}</span>
          @if (movie().averageRating !== null) {
            <span class="rating">★ {{ movie().averageRating | number: '1.1-1' }} <span class="muted">({{ movie().ratingCount }})</span></span>
          }
        </div>
        <div class="genres">{{ movie().genres.slice(0, 2).join(' · ') }}</div>
        <ng-content />
      </div>
    </a>
  `,
  styles: `
    .card { display: block; transition: transform .15s; }
    .card:hover { transform: translateY(-4px); }
    .card:hover app-poster { box-shadow: 0 12px 28px rgb(0 0 0 / .5), 0 0 0 2px var(--accent); }
    .meta { padding: 8px 2px 0; }
    .title { font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .sub { display: flex; justify-content: space-between; font-size: .82rem; color: var(--text-dim); }
    .rating { color: var(--gold); }
    .genres { font-size: .78rem; color: var(--text-dim); }
  `,
})
export class MovieCard {
  readonly movie = input.required<Movie>();
}
