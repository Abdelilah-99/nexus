import { Component, OnInit, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { debounceTime } from 'rxjs';
import { Genre } from '../../core/models';
import { MovieSearch } from '../../core/api.service';

/** Search by title, genre, release year range, release date range, minimum rating; plus sorting. */
@Component({
  selector: 'app-search-bar',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" class="bar panel" (ngSubmit)="emit()">
      <input class="input title" formControlName="title" placeholder="Search a title…" aria-label="Title" type="search" />
      <select class="input" formControlName="genre" aria-label="Genre">
        <option value="">All genres</option>
        @for (g of genres(); track g.name) {
          <option [value]="g.name">{{ g.name }} ({{ g.movieCount }})</option>
        }
      </select>
      <select class="input" formControlName="sort" aria-label="Sort by">
        <option value="title">A → Z</option>
        <option value="year">Newest</option>
        <option value="oldest">Oldest</option>
        <option value="rating">Best rated</option>
        <option value="popular">Most rated</option>
      </select>
      <button type="button" class="btn" (click)="advanced = !advanced" [attr.aria-expanded]="advanced">
        Filters {{ advanced ? '▴' : '▾' }}
      </button>
      @if (advanced) {
        <div class="advanced">
          <div><label for="yf">Year from</label><input id="yf" class="input" type="number" formControlName="yearFrom" min="1900" max="2100" /></div>
          <div><label for="yt">Year to</label><input id="yt" class="input" type="number" formControlName="yearTo" min="1900" max="2100" /></div>
          <div><label for="ra">Released after</label><input id="ra" class="input" type="date" formControlName="releasedAfter" /></div>
          <div><label for="rb">Released before</label><input id="rb" class="input" type="date" formControlName="releasedBefore" /></div>
          <div>
            <label for="mr">Minimum rating</label>
            <select id="mr" class="input" formControlName="minRating">
              <option [ngValue]="null">Any</option>
              <option [ngValue]="3">★ 3+</option>
              <option [ngValue]="4">★ 4+</option>
              <option [ngValue]="4.5">★ 4.5+</option>
            </select>
          </div>
          <div class="reset"><button type="button" class="btn btn-ghost" (click)="reset()">Reset</button></div>
        </div>
      }
    </form>
  `,
  styles: `
    .bar { display: grid; grid-template-columns: 1fr 180px 150px auto; gap: 10px; padding: 12px; }
    .advanced { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; align-items: end; }
    .reset { display: flex; justify-content: flex-end; }
    @media (max-width: 720px) { .bar { grid-template-columns: 1fr 1fr; } .title { grid-column: 1 / -1; } }
  `,
})
export class SearchBar implements OnInit {
  readonly genres = input<Genre[]>([]);
  readonly initial = input<MovieSearch>({});
  readonly search = output<MovieSearch>();

  protected advanced = false;
  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    title: [''],
    genre: [''],
    sort: ['title'],
    yearFrom: [null as number | null],
    yearTo: [null as number | null],
    releasedAfter: [''],
    releasedBefore: [''],
    minRating: [null as number | null],
  });

  ngOnInit(): void {
    const i = this.initial();
    this.form.patchValue({ ...i, sort: i.sort ?? 'title' }, { emitEvent: false });
    this.advanced = !!(i.yearFrom || i.yearTo || i.releasedAfter || i.releasedBefore || i.minRating);
    // Search as you type, but wait 300 ms after the last keystroke so we don't call the API on every key.
    this.form.valueChanges.pipe(debounceTime(300)).subscribe(() => this.emit());
  }

  emit(): void {
    const v = this.form.getRawValue();
    this.search.emit({
      title: v.title ?? '', genre: v.genre ?? '', sort: v.sort ?? 'title',
      yearFrom: v.yearFrom, yearTo: v.yearTo, releasedAfter: v.releasedAfter ?? '',
      releasedBefore: v.releasedBefore ?? '', minRating: v.minRating,
    });
  }

  reset(): void {
    this.form.reset({ title: '', genre: '', sort: 'title', yearFrom: null, yearTo: null, releasedAfter: '', releasedBefore: '', minRating: null });
  }
}
