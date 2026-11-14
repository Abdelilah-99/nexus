export interface User {
  id: string;
  username: string;
  email: string;
  displayName: string;
  bio: string | null;
  roles: string[];
  createdAt: string;
  twoFactorEnabled: boolean;
}

export interface PublicUser {
  id: string;
  username: string;
  displayName: string | null;
  bio: string | null;
}

export interface LoginResponse {
  accessToken: string | null;
  tokenType: string | null;
  expiresIn: number | null;
  user: User | null;
  mfaRequired: boolean;
  mfaToken: string | null;
}

export interface Movie {
  id: string;
  title: string;
  releaseDate: string | null;
  releaseYear: number | null;
  runtime: number | null;
  director: string | null;
  overview: string | null;
  posterUrl: string | null;
  genres: string[];
  averageRating: number | null;
  ratingCount: number;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface Genre {
  name: string;
  movieCount: number;
}

export interface SimilarMovie {
  movie: Movie;
  score: number;
  reasons: string[];
}

export interface Rating {
  userId: string;
  username: string;
  movieId: string;
  movieTitle: string;
  movieYear: number | null;
  score: number;
  review: string | null;
  ratedAt: string;
  updatedAt: string;
}

export interface MovieRatings {
  movieId: string;
  averageRating: number | null;
  ratingCount: number;
  distribution: Record<string, number>;
  ratings: Rating[];
}

export interface Recommendation {
  movie: Movie;
  score: number;
  strategy: 'hybrid' | 'collaborative' | 'content' | 'popular';
  reasons: string[];
  inWatchlist: boolean;
}

export interface WatchlistItem {
  movieId: string;
  title: string;
  releaseYear: number | null;
  genres: string[];
  posterUrl: string | null;
  addedAt: string;
}

export interface Friend {
  id: string;
  username: string;
  displayName: string | null;
  since: string;
}

export interface Share {
  id: string;
  fromUserId: string;
  fromUsername: string;
  toUserId: string;
  toUsername: string;
  movie: Movie;
  message: string | null;
  sharedAt: string;
  read: boolean;
}

export interface ApiError {
  status: number;
  message: string;
  details: string[];
}

/** Extracts a readable message from an HttpErrorResponse coming from any Neo4flix service. */
export function errorMessage(err: unknown): string {
  const e = err as { status?: number; error?: Partial<ApiError> };
  if (e?.status === 0) {
    return 'Cannot reach the server. Check your connection.';
  }
  const body = e?.error;
  if (body?.details?.length) {
    return body.details.join(' · ');
  }
  return body?.message ?? 'Something went wrong. Please try again.';
}
