package com.neo4flix.movie.web;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MovieDtos {

    private MovieDtos() {
    }

    public record Movie(String id, String title, LocalDate releaseDate, Integer releaseYear, Integer runtime,
            String director, String overview, String posterUrl, List<String> genres, Double averageRating,
            long ratingCount) {
    }

    public record MovieRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate releaseDate,
            @Min(1) @Max(1000) Integer runtime,
            @Size(max = 100) String director,
            @Size(max = 2000) String overview,
            @Size(max = 500) String posterUrl,
            @NotEmpty List<@NotBlank @Size(max = 40) String> genres) {
    }

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    public record Genre(String name, long movieCount) {
    }

    /** Filters for GET /api/movies; every field is optional and they are combined with AND. */
    public record SearchCriteria(String title, String genre, Integer year, Integer yearFrom, Integer yearTo,
            LocalDate releasedAfter, LocalDate releasedBefore, Double minRating, String sort, int page, int size) {
    }
}
