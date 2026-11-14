import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Share, errorMessage } from '../../core/models';
import { SharesStore } from '../../core/shares.store';
import { ToastService } from '../../core/toast.service';
import { Poster } from '../../shared/poster';

/** Movies my friends recommended to me (inbox) and the ones I sent. */
@Component({
  selector: 'app-shared',
  imports: [RouterLink, Poster, DatePipe],
  template: `
    <div class="container page">
      <div class="page-header">
        <h1>Shared recommendations</h1>
        <div class="row">
          <button class="chip" [class.active]="tab() === 'inbox'" (click)="show('inbox')">Received</button>
          <button class="chip" [class.active]="tab() === 'sent'" (click)="show('sent')">Sent</button>
        </div>
      </div>
      @if (loading()) {
        <div class="spinner"></div>
      } @else {
        <div class="list">
          @for (s of shares(); track s.id) {
            <article class="share panel" [class.unread]="tab() === 'inbox' && !s.read">
              <a [routerLink]="['/movies', s.movie.id]" (click)="open(s)">
                <app-poster [id]="s.movie.id" [title]="s.movie.title" [year]="s.movie.releaseYear" [url]="s.movie.posterUrl" />
              </a>
              <div class="stack body">
                <div class="muted small">
                  @if (tab() === 'inbox') { From <strong>{{ s.fromUsername }}</strong> } @else { To <strong>{{ s.toUsername }}</strong> }
                  · {{ s.sharedAt | date: 'medium' }}
                  @if (tab() === 'inbox' && !s.read) { <span class="chip new">New</span> }
                </div>
                <a [routerLink]="['/movies', s.movie.id]" (click)="open(s)"><strong>{{ s.movie.title }}</strong> ({{ s.movie.releaseYear }})</a>
                @if (s.message) { <p class="message">“{{ s.message }}”</p> }
                <div class="row">
                  @if (tab() === 'inbox' && !s.read) { <button class="btn btn-sm" (click)="markRead(s)">Mark as read</button> }
                  <button class="btn btn-sm btn-ghost btn-danger" (click)="remove(s)">Delete</button>
                </div>
              </div>
            </article>
          } @empty {
            <div class="empty">{{ tab() === 'inbox' ? 'No friend has shared a movie with you yet.' : 'You have not shared any movie yet.' }}</div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .list { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(min(100%, 480px), 1fr)); }
    .share { display: grid; grid-template-columns: 80px 1fr; gap: 14px; padding: 12px; }
    .share.unread { border-color: var(--accent); }
    .body { gap: 6px; min-width: 0; }
    .message { margin: 0; font-style: italic; color: #cfd5e2; }
    .new { background: var(--accent); border-color: var(--accent); color: #fff; margin-left: 6px; }
  `,
})
export class Shared {
  private readonly api = inject(ApiService);
  private readonly store = inject(SharesStore);
  private readonly toast = inject(ToastService);
  protected readonly tab = signal<'inbox' | 'sent'>('inbox');
  protected readonly shares = signal<Share[]>([]);
  protected readonly loading = signal(true);

  constructor() {
    this.show('inbox');
  }

  show(tab: 'inbox' | 'sent'): void {
    this.tab.set(tab);
    this.loading.set(true);
    (tab === 'inbox' ? this.api.inbox() : this.api.sent()).subscribe({
      next: (s) => {
        this.shares.set(s);
        this.loading.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  open(share: Share): void {
    if (this.tab() === 'inbox' && !share.read) {
      this.api.markShareRead(share.id).subscribe(() => this.store.refresh());
    }
  }

  markRead(share: Share): void {
    this.api.markShareRead(share.id).subscribe(() => {
      this.shares.update((list) => list.map((s) => (s.id === share.id ? { ...s, read: true } : s)));
      this.store.refresh();
    });
  }

  remove(share: Share): void {
    this.api.deleteShare(share.id).subscribe({
      next: () => {
        this.shares.update((list) => list.filter((s) => s.id !== share.id));
        this.store.refresh();
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }
}
