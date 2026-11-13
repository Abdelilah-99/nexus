package com.neo4flix.rating.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RatingDtos {

    private RatingDtos() {
    }

    /** Score from 1 to 5 stars, with an optional short review. */
    public record RateRequest(@NotNull @Min(1) @Max(5) Integer score, @Size(max = 1000) String review) {
    }

    public record CreateRatingRequest(@NotBlank String movieId, @NotNull @Min(1) @Max(5) Integer score,
            @Size(max = 1000) String review) {
    }

    public record Rating(String userId, String username, String movieId, String movieTitle, Integer movieYear,
            int score, String review, Instant ratedAt, Instant updatedAt) {
    }

    /** distribution: number of ratings per score, e.g. {"5": 12, "4": 3, ...}. */
    public record MovieRatings(String movieId, Double averageRating, long ratingCount, Map<String, Long> distribution,
            List<Rating> ratings) {
    }
}
