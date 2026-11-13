package com.neo4flix.recommendation.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record MovieCard(String id, String title, LocalDate releaseDate, Integer releaseYear, List<String> genres,
            String posterUrl, Double averageRating, long ratingCount) {
    }

    /** strategy: which algorithm produced it ("collaborative", "content", "hybrid" or "popular"). */
    public record Recommendation(MovieCard movie, double score, String strategy, List<String> reasons,
            boolean inWatchlist) {
    }

    /** Filters accepted by GET /api/recommendations (all optional). */
    public record Filters(String genre, Integer yearFrom, Integer yearTo, LocalDate releasedAfter,
            LocalDate releasedBefore) {
    }

    public record ShareRequest(@NotBlank String movieId, @NotBlank String friendId, @Size(max = 300) String message) {
    }

    public record Share(String id, String fromUserId, String fromUsername, String toUserId, String toUsername,
            MovieCard movie, String message, Instant sharedAt, boolean read) {
    }
}
