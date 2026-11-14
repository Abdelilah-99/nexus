import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Rating, errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Stars } from '../../shared/stars';

/** "Your rating" panel on the movie page: create, update or delete my rating and review. */
@Component({
  selector: 'app-rate-box',
  imports: [Stars, FormsModule, RouterLink],
  template: `
    <div class="panel stack">
      <h2>Your rating</h2>
      @if (!auth.isLoggedIn()) {
        <p class="muted"><a class="link" routerLink="/login">Log in</a> to rate this movie.</p>
      } @else {
        <app-stars [editable]="true" [(value)]="score" />
        <textarea class="input" rows="3" maxlength="1000" placeholder="Write a short review (optional)"
                  [ngModel]="review()" (ngModelChange)="review.set($event)" aria-label="Review"></textarea>
        <div class="row">
          <button class="btn btn-primary" [disabled]="!score() || busy()" (click)="save()">
            {{ existing() ? 'Update rating' : 'Rate' }}
          </button>
          @if (existing()) {
            <button class="btn btn-danger btn-ghost" [disabled]="busy()" (click)="remove()">Delete</button>
            <span class="muted small">Rated {{ existing()!.score }}★</span>
          }
        </div>
      }
    </div>
  `,
})
export class RateBox {
  readonly movieId = input.required<string>();
  /** Emitted after a change so the page can refresh the average and the reviews. */
  readonly changed = output<void>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  protected readonly existing = signal<Rating | null>(null);
  protected readonly score = signal<number | null>(null);
  protected readonly review = signal('');
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      const id = this.movieId();
      this.existing.set(null);
      this.score.set(null);
      this.review.set('');
      if (this.auth.isLoggedIn()) {
        // 404 simply means "not rated yet".
        this.api.myRating(id).subscribe({ next: (r) => this.fill(r), error: () => undefined });
      }
    });
  }

  save(): void {
    this.busy.set(true);
    this.api.rate(this.movieId(), this.score()!, this.review().trim() || null).subscribe({
      next: (r) => {
        this.toast.success(this.existing() ? 'Rating updated' : 'Thanks for rating!');
        this.fill(r);
        this.busy.set(false);
        this.changed.emit();
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  remove(): void {
    this.busy.set(true);
    this.api.deleteRating(this.movieId()).subscribe({
      next: () => {
        this.toast.info('Rating deleted');
        this.existing.set(null);
        this.score.set(null);
        this.review.set('');
        this.busy.set(false);
        this.changed.emit();
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  private fill(r: Rating): void {
    this.existing.set(r);
    this.score.set(r.score);
    this.review.set(r.review ?? '');
  }
}
