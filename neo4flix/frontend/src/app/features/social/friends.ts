import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Friend, PublicUser, errorMessage } from '../../core/models';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-friends',
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    <div class="container page">
      <h1>Friends</h1>
      <p class="muted">Add friends to share movie recommendations with them.</p>
      <div class="cols">
        <section class="panel stack">
          <h2>Find people</h2>
          <input class="input" type="search" [formControl]="query" placeholder="Search by username (2+ letters)" aria-label="Search users" />
          @for (u of results(); track u.id) {
            <div class="person">
              <div><strong>{{ u.displayName || u.username }}</strong> <span class="muted small">&#64;{{ u.username }}</span></div>
              @if (isFriend(u.id)) {
                <span class="chip">Friend</span>
              } @else {
                <button class="btn btn-sm btn-primary" (click)="add(u)">Add</button>
              }
            </div>
          } @empty {
            @if ((query.value ?? '').length >= 2) { <p class="muted small">Nobody found.</p> }
          }
        </section>
        <section class="panel stack">
          <h2>My friends ({{ friends().length }})</h2>
          @for (f of friends(); track f.id) {
            <div class="person">
              <div>
                <strong>{{ f.displayName || f.username }}</strong> <span class="muted small">&#64;{{ f.username }}</span>
                <div class="muted small">Friends since {{ f.since | date: 'mediumDate' }}</div>
              </div>
              <button class="btn btn-sm btn-ghost btn-danger" (click)="remove(f)">Remove</button>
            </div>
          } @empty {
            <p class="muted">No friends yet. Search for someone on the left.</p>
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 16px; }
    .person { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--border); }
    @media (max-width: 760px) { .cols { grid-template-columns: 1fr; } }
  `,
})
export class Friends {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly query = new FormControl('');
  protected readonly results = signal<PublicUser[]>([]);
  protected readonly friends = signal<Friend[]>([]);

  constructor() {
    this.loadFriends();
    this.query.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        // switchMap cancels the previous request when the user keeps typing.
        switchMap((q) => ((q ?? '').trim().length >= 2 ? this.api.searchUsers(q!.trim()) : of([]))),
      )
      .subscribe((users) => this.results.set(users));
  }

  isFriend(id: string): boolean {
    return this.friends().some((f) => f.id === id);
  }

  add(user: PublicUser): void {
    this.api.addFriend(user.id).subscribe({
      next: () => {
        this.toast.success(`${user.username} added to your friends`);
        this.loadFriends();
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  remove(friend: Friend): void {
    this.api.removeFriend(friend.id).subscribe({
      next: () => {
        this.friends.update((list) => list.filter((f) => f.id !== friend.id));
        this.toast.info(`${friend.username} removed`);
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  private loadFriends(): void {
    this.api.friends().subscribe((f) => this.friends.set(f));
  }
}
