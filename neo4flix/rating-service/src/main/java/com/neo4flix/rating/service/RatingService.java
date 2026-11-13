package com.neo4flix.rating.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.neo4flix.rating.client.MovieClient;
import com.neo4flix.rating.web.RatingDtos.MovieRatings;
import com.neo4flix.rating.web.RatingDtos.Rating;
import com.neo4flix.security.ApiException;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * A rating is a relationship with properties: (:User)-[:RATED {score, review, ratedAt, updatedAt}]->(:Movie).
 * At most one RATED relationship exists per user and movie (enforced by MERGE).
 */
@Service
public class RatingService {

    private static final String RETURN_RATING = """
            RETURN u.id AS userId, u.username AS username, m.id AS movieId, m.title AS movieTitle,
                   m.releaseYear AS movieYear, r.score AS score, r.review AS review,
                   r.ratedAt AS ratedAt, r.updatedAt AS updatedAt
            """;

    private final Neo4jClient neo4j;
    private final MovieClient movies;

    public RatingService(Neo4jClient neo4j, MovieClient movies) {
        this.neo4j = neo4j;
        this.movies = movies;
    }

    public Rating create(String userId, String movieId, int score, String review) {
        movies.get(movieId);
        if (find(userId, movieId) != null) {
            throw ApiException.conflict("You already rated this movie; use PUT to change your rating");
        }
        return upsert(userId, movieId, score, review);
    }

    /** Creates the rating or updates the existing one (score, review and updatedAt). */
    public Rating rate(String userId, String movieId, int score, String review) {
        movies.get(movieId);
        return upsert(userId, movieId, score, review);
    }

    private Rating upsert(String userId, String movieId, int score, String review) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("movieId", movieId);
        params.put("score", score);
        params.put("review", review == null || review.isBlank() ? null : review.trim());
        return neo4j.query("""
                        MATCH (u:User {id: $userId}), (m:Movie {id: $movieId})
                        MERGE (u)-[r:RATED]->(m)
                        ON CREATE SET r.ratedAt = datetime()
                        SET r.score = $score, r.review = $review, r.updatedAt = datetime()
                        """ + RETURN_RATING)
                .bindAll(params).fetchAs(Rating.class).mappedBy((t, r) -> toRating(r)).one()
                .orElseThrow(() -> ApiException.notFound("User or movie not found"));
    }

    public Rating mine(String userId, String movieId) {
        Rating rating = find(userId, movieId);
        if (rating == null) {
            throw ApiException.notFound("You have not rated this movie");
        }
        return rating;
    }

    public List<Rating> byUser(String userId) {
        return List.copyOf(neo4j.query("MATCH (u:User {id: $userId})-[r:RATED]->(m:Movie)\n" + RETURN_RATING
                        + "ORDER BY r.updatedAt DESC")
                .bind(userId).to("userId").fetchAs(Rating.class).mappedBy((t, r) -> toRating(r)).all());
    }

    public MovieRatings byMovie(String movieId, int limit) {
        movies.get(movieId);
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int s = 5; s >= 1; s--) {
            distribution.put(String.valueOf(s), 0L);
        }
        neo4j.query("MATCH (:User)-[r:RATED]->(:Movie {id: $movieId}) RETURN r.score AS score, count(*) AS n")
                .bind(movieId).to("movieId")
                .fetch().all()
                .forEach(row -> distribution.put(String.valueOf(((Number) row.get("score")).intValue()),
                        ((Number) row.get("n")).longValue()));
        long count = distribution.values().stream().mapToLong(Long::longValue).sum();
        double sum = distribution.entrySet().stream()
                .mapToDouble(e -> Integer.parseInt(e.getKey()) * e.getValue()).sum();
        List<Rating> latest = List.copyOf(neo4j.query("MATCH (u:User)-[r:RATED]->(m:Movie {id: $movieId})\n"
                        + RETURN_RATING + "ORDER BY r.updatedAt DESC LIMIT $limit")
                .bindAll(Map.of("movieId", movieId, "limit", (long) Math.clamp(limit, 1, 100)))
                .fetchAs(Rating.class).mappedBy((t, r) -> toRating(r)).all());
        Double average = count == 0 ? null : Math.round(sum / count * 10) / 10.0;
        return new MovieRatings(movieId, average, count, distribution, latest);
    }

    public void delete(String userId, String movieId) {
        long deleted = neo4j.query("""
                        MATCH (:User {id: $userId})-[r:RATED]->(:Movie {id: $movieId})
                        DELETE r RETURN count(*) AS deleted
                        """)
                .bindAll(Map.of("userId", userId, "movieId", movieId)).fetchAs(Long.class).one().orElse(0L);
        if (deleted == 0) {
            throw ApiException.notFound("Rating not found");
        }
    }

    private Rating find(String userId, String movieId) {
        return neo4j.query("MATCH (u:User {id: $userId})-[r:RATED]->(m:Movie {id: $movieId})\n" + RETURN_RATING)
                .bindAll(Map.of("userId", userId, "movieId", movieId))
                .fetchAs(Rating.class).mappedBy((t, r) -> toRating(r)).one().orElse(null);
    }

    private static Rating toRating(Record r) {
        return new Rating(r.get("userId").asString(), r.get("username").asString(), r.get("movieId").asString(),
                r.get("movieTitle").asString(), r.get("movieYear").isNull() ? null : r.get("movieYear").asInt(),
                r.get("score").asInt(), r.get("review").isNull() ? null : r.get("review").asString(),
                instant(r.get("ratedAt")), instant(r.get("updatedAt")));
    }

    private static Instant instant(Value value) {
        return value.isNull() ? null : value.asZonedDateTime().toInstant();
    }
}
