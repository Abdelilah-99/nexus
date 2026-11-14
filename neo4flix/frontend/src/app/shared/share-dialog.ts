import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Friend, errorMessage } from '../core/models';
import { ToastService } from '../core/toast.service';

/** Modal: send a movie to a friend (with an optional message), or copy its link to share anywhere. */
@Component({
  selector: 'app-share-dialog',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="backdrop" (click)="closed.emit()"></div>
    <div class="dialog panel stack" role="dialog" aria-modal="true" aria-labelledby="share-title">
      <h2 id="share-title">Share “{{ movieTitle() }}”</h2>
      @if (loading()) {
        <div class="spinner"></div>
      } @else if (!friends().length) {
        <p class="muted">You have no friends on Neo4flix yet. <a class="link" routerLink="/friends" (click)="closed.emit()">Find friends</a></p>
      } @else {
        <div>
          <label for="friend">Send to</label>
          <select id="friend" class="input" [ngModel]="friendId()" (ngModelChange)="friendId.set($event)">
            @for (f of friends(); track f.id) { <option [value]="f.id">{{ f.displayName || f.username }} (&#64;{{ f.username }})</option> }
          </select>
        </div>
        <div>
          <label for="msg">Message (optional)</label>
          <textarea id="msg" class="input" rows="3" maxlength="300" [ngModel]="message()" (ngModelChange)="message.set($event)" placeholder="You have to watch this!"></textarea>
        </div>
        <button class="btn btn-primary" [disabled]="!friendId() || busy()" (click)="send()">Send recommendation</button>
      }
      <div class="row">
        <button class="btn btn-sm" (click)="copyLink()">🔗 Copy link</button>
        <span class="spacer"></span>
        <button class="btn btn-sm btn-ghost" (click)="closed.emit()">Close</button>
      </div>
    </div>
  `,
  styles: `
    .backdrop { position: fixed; inset: 0; background: rgb(0 0 0 / .6); z-index: 40; }
    .dialog { position: fixed; z-index: 41; top: 50%; left: 50%; transform: translate(-50%, -50%); width: min(440px, calc(100% - 32px)); box-shadow: var(--shadow); }
  `,
})
export class ShareDialog {
  readonly movieId = input.required<string>();
  readonly movieTitle = input.required<string>();
  readonly closed = output<void>();

  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly friends = signal<Friend[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly friendId = signal('');
  protected readonly message = signal('');

  constructor() {
    this.api.friends().subscribe({
      next: (f) => {
        this.friends.set(f);
        this.friendId.set(f[0]?.id ?? '');
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  send(): void {
    this.busy.set(true);
    this.api.share(this.movieId(), this.friendId(), this.message().trim() || null).subscribe({
      next: (s) => {
        this.toast.success(`Sent to ${s.toUsername}`);
        this.closed.emit();
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  async copyLink(): Promise<void> {
    const url = `${location.origin}/movies/${this.movieId()}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: this.movieTitle(), text: `Watch ${this.movieTitle()} on Neo4flix`, url });
        return;
      }
      await navigator.clipboard.writeText(url);
      this.toast.success('Link copied to the clipboard');
    } catch {
      this.toast.info(url);
    }
  }
}
