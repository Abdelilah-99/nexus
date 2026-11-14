import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Friend, Genre, Movie, MovieRatings, Page, PublicUser, Rating, Recommendation, Share, SimilarMovie, User, WatchlistItem,
} from './models';

export interface MovieSearch {
  title?: string;
  genre?: string;
  yearFrom?: number | null;
  yearTo?: number | null;
  releasedAfter?: string;
  releasedBefore?: string;
  minRating?: number | null;
  sort?: string;
  page?: number;
  size?: number;
}

export interface RecommendationFilters {
  genre?: string;
  yearFrom?: number | null;
  yearTo?: number | null;
  releasedAfter?: string;
  releasedBefore?: string;
  strategy?: string;
  limit?: number;
}

/** Drops empty values so the URL only carries the filters actually used. */
function params(values: object): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      p = p.set(key, String(value));
    }
  }
  return p;
}

/** One method per REST endpoint; every call goes through the gateway at /api. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // movie-service
  searchMovies(search: MovieSearch): Observable<Page<Movie>> {
    return this.http.get<Page<Movie>>('/api/movies', { params: params(search) });
  }
  movie(id: string): Observable<Movie> {
    return this.http.get<Movie>(`/api/movies/${encodeURIComponent(id)}`);
  }
  similar(id: string, limit = 8): Observable<SimilarMovie[]> {
    return this.http.get<SimilarMovie[]>(`/api/movies/${encodeURIComponent(id)}/similar`, { params: params({ limit }) });
  }
  genres(): Observable<Genre[]> {
    return this.http.get<Genre[]>('/api/movies/genres');
  }

  // rating-service
  movieRatings(movieId: string): Observable<MovieRatings> {
    return this.http.get<MovieRatings>(`/api/ratings/movie/${encodeURIComponent(movieId)}`);
  }
  myRatings(): Observable<Rating[]> {
    return this.http.get<Rating[]>('/api/ratings/me');
  }
  myRating(movieId: string): Observable<Rating> {
    return this.http.get<Rating>(`/api/ratings/me/${encodeURIComponent(movieId)}`);
  }
  rate(movieId: string, score: number, review: string | null): Observable<Rating> {
    return this.http.put<Rating>(`/api/ratings/${encodeURIComponent(movieId)}`, { score, review });
  }
  deleteRating(movieId: string): Observable<void> {
    return this.http.delete<void>(`/api/ratings/${encodeURIComponent(movieId)}`);
  }

  // recommendation-service
  recommendations(filters: RecommendationFilters): Observable<Recommendation[]> {
    return this.http.get<Recommendation[]>('/api/recommendations', { params: params(filters) });
  }
  share(movieId: string, friendId: string, message: string | null): Observable<Share> {
    return this.http.post<Share>('/api/recommendations/share', { movieId, friendId, message });
  }
  inbox(): Observable<Share[]> {
    return this.http.get<Share[]>('/api/recommendations/shared/inbox');
  }
  sent(): Observable<Share[]> {
    return this.http.get<Share[]>('/api/recommendations/shared/sent');
  }
  unreadShares(): Observable<{ unread: number }> {
    return this.http.get<{ unread: number }>('/api/recommendations/shared/unread-count');
  }
  markShareRead(id: string): Observable<void> {
    return this.http.post<void>(`/api/recommendations/shared/${id}/read`, {});
  }
  deleteShare(id: string): Observable<void> {
    return this.http.delete<void>(`/api/recommendations/shared/${id}`);
  }

  // user-service
  me(): Observable<User> {
    return this.http.get<User>('/api/users/me');
  }
  updateMe(body: { displayName?: string; bio?: string; email?: string }): Observable<User> {
    return this.http.put<User>('/api/users/me', body);
  }
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>('/api/users/me/password', { currentPassword, newPassword });
  }
  watchlist(): Observable<WatchlistItem[]> {
    return this.http.get<WatchlistItem[]>('/api/users/me/watchlist');
  }
  addToWatchlist(movieId: string): Observable<void> {
    return this.http.put<void>(`/api/users/me/watchlist/${encodeURIComponent(movieId)}`, {});
  }
  removeFromWatchlist(movieId: string): Observable<void> {
    return this.http.delete<void>(`/api/users/me/watchlist/${encodeURIComponent(movieId)}`);
  }
  searchUsers(q: string): Observable<PublicUser[]> {
    return this.http.get<PublicUser[]>('/api/users/search', { params: params({ q }) });
  }
  friends(): Observable<Friend[]> {
    return this.http.get<Friend[]>('/api/users/me/friends');
  }
  addFriend(userId: string): Observable<void> {
    return this.http.put<void>(`/api/users/me/friends/${userId}`, {});
  }
  removeFriend(userId: string): Observable<void> {
    return this.http.delete<void>(`/api/users/me/friends/${userId}`);
  }
  setupTwoFactor(): Observable<{ secret: string; otpauthUri: string }> {
    return this.http.post<{ secret: string; otpauthUri: string }>('/api/auth/2fa/setup', {});
  }
  enableTwoFactor(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>('/api/auth/2fa/enable', { code });
  }
  disableTwoFactor(password: string, code: string): Observable<void> {
    return this.http.post<void>('/api/auth/2fa/disable', { password, code });
  }
}
