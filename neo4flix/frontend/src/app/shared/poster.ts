import { Component, computed, input } from '@angular/core';

/**
 * Movie poster. When the catalogue has no image URL, draws a stable gradient from the movie id
 * (same movie = same colours) with the title on it, so no third-party image host is needed.
 */
@Component({
  selector: 'app-poster',
  template: `
    @if (url()) {
      <img [src]="url()" [alt]="title()" loading="lazy" />
    } @else {
      <div class="fallback" [style.background]="gradient()">
        <span>{{ title() }}</span>
        @if (year()) { <small>{{ year() }}</small> }
      </div>
    }
  `,
  styles: `
    :host { display: block; aspect-ratio: 2 / 3; border-radius: 10px; overflow: hidden; background: var(--bg-elev-2); }
    img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .fallback { width: 100%; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; padding: 14px; }
    span { font-weight: 800; font-size: 1.05rem; line-height: 1.15; text-shadow: 0 2px 8px rgb(0 0 0 / .5); }
    small { opacity: .8; margin-top: 4px; }
  `,
})
export class Poster {
  readonly id = input.required<string>();
  readonly title = input.required<string>();
  readonly year = input<number | null>(null);
  readonly url = input<string | null>(null);

  protected readonly gradient = computed(() => {
    let hash = 0;
    for (const ch of this.id()) {
      hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    }
    const h1 = Math.abs(hash) % 360;
    const h2 = (h1 + 40 + (Math.abs(hash >> 8) % 80)) % 360;
    return `linear-gradient(160deg, hsl(${h1} 55% 32%), hsl(${h2} 60% 14%))`;
  });
}
