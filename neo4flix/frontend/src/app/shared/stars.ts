import { Component, computed, input, model } from '@angular/core';

/** Shows a rating as stars; with [editable]="true" it becomes a 1–5 star picker (keyboard accessible). */
@Component({
  selector: 'app-stars',
  template: `
    <span class="stars" [class.editable]="editable()" role="group" [attr.aria-label]="label()">
      @for (s of five; track s) {
        @if (editable()) {
          <button type="button" [class.on]="s <= (hover || value() || 0)" (mouseenter)="hover = s" (mouseleave)="hover = 0"
                  (click)="value.set(s)" [attr.aria-label]="s + (s === 1 ? ' star' : ' stars')">★</button>
        } @else {
          <span [class.on]="s <= rounded()" [class.half]="s === rounded() + 1 && hasHalf()">★</span>
        }
      }
    </span>
  `,
  styles: `
    .stars { display: inline-flex; gap: 2px; color: #3a4152; font-size: var(--star-size, 1rem); line-height: 1; }
    .on { color: var(--gold); }
    .half { background: linear-gradient(90deg, var(--gold) 50%, #3a4152 50%); -webkit-background-clip: text; background-clip: text; color: transparent; }
    button { background: none; border: 0; padding: 0 1px; font-size: inherit; color: #3a4152; cursor: pointer; }
    button.on { color: var(--gold); }
    .editable { --star-size: 1.8rem; }
  `,
})
export class Stars {
  readonly value = model<number | null>(null);
  readonly editable = input(false);
  protected readonly five = [1, 2, 3, 4, 5];
  protected hover = 0;
  protected readonly rounded = computed(() => Math.floor(this.value() ?? 0));
  protected readonly hasHalf = computed(() => (this.value() ?? 0) - this.rounded() >= 0.5);
  protected readonly label = computed(() => (this.value() ? `${this.value()} out of 5` : 'Not rated'));
}
