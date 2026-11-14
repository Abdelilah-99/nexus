import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';

/** Number of unread shares, shown as a badge in the navbar. */
@Injectable({ providedIn: 'root' })
export class SharesStore {
  private readonly api = inject(ApiService);
  readonly unread = signal(0);

  refresh(): void {
    this.api.unreadShares().subscribe({ next: (r) => this.unread.set(r.unread), error: () => undefined });
  }
}
