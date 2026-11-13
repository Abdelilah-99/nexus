package com.neo4flix.recommendation.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.neo4flix.recommendation.service.RecommendationRanker.Candidate;
import com.neo4flix.recommendation.service.RecommendationRanker.Ranked;
import com.neo4flix.recommendation.web.RecommendationDtos.Filters;
import com.neo4flix.recommendation.web.RecommendationDtos.MovieCard;
import com.neo4flix.recommendation.web.RecommendationDtos.Recommendation;
import com.neo4flix.security.ApiException;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Personalised recommendations computed by graph traversal in Neo4j.
 * Movies the user already rated are never recommended.
 */
@Service
public class RecommendationService {

    private static final int CANDIDATES = 100;

    /**
     * User-based collaborative filtering.
     * 1. Neighbours: users who rated the same movies as me; similarity = agreement on those ratings
     *    (1 - mean |difference| / 4) damped when they have few movies in common.
     * 2. Movies my 30 closest neighbours loved (4-5 stars) that I have not rated, weighted by similarity.
     */
    static final String COLLABORATIVE = """
            MATCH (me:User {id: $userId})-[r1:RATED]->(m:Movie)<-[r2:RATED]-(other:User)
            WHERE other <> me
            WITH me, other, count(m) AS common, avg(abs(r1.score - r2.score)) AS meanDiff
            WITH me, other, (1.0 - meanDiff / 4.0) * (toFloat(common) / (common + 2)) AS similarity
            WHERE similarity > 0
            ORDER BY similarity DESC
            LIMIT 30
            MATCH (other)-[r:RATED]->(rec:Movie)
            WHERE r.score >= 4 AND NOT EXISTS { (me)-[:RATED]->(rec) } %s
            WITH rec, sum(similarity * (r.score - 3)) AS score, collect(DISTINCT other.username) AS fans
            RETURN rec.id AS id, score, fans
            ORDER BY score DESC
            LIMIT $candidates
            """;

    /**
     * Content-based filtering: my affinity for each genre = sum of (my score - 3) over the movies of that genre
     * I rated (a 5 counts +2, a 1 counts -2). Unseen movies get the sum of the affinities of their genres.
     */
    static final String CONTENT = """
            MATCH (me:User {id: $userId})-[r:RATED]->(:Movie)-[:IN_GENRE]->(g:Genre)
            WITH me, g, sum(r.score - 3) AS affinity
            WHERE affinity > 0
            MATCH (g)<-[:IN_GENRE]-(rec:Movie)
            WHERE NOT EXISTS { (me)-[:RATED]->(rec) } %s
            WITH rec, sum(affinity) AS score, collect(g.name) AS genres
            RETURN rec.id AS id, score, genres
            ORDER BY score DESC
            LIMIT $candidates
            """;

    /** Cold start / filler: best-rated movies, weighted by the number of ratings (log damps the very popular). */
    static final String POPULAR = """
            MATCH (rec:Movie)
            WHERE NOT EXISTS { (:User {id: $userId})-[:RATED]->(rec) } %s
            OPTIONAL MATCH (:User)-[r:RATED]->(rec)
            WITH rec, avg(r.score) AS average, count(r) AS ratings
            RETURN rec.id AS id, coalesce(average, 0) * log(2 + ratings) AS score, ratings
            ORDER BY score DESC, rec.releaseDate DESC
            LIMIT $candidates
            """;

    static final String CARDS = """
            UNWIND $ids AS movieId
            MATCH (m:Movie {id: movieId})
            OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre)
            WITH m, collect(g.name) AS genres
            OPTIONAL MATCH (:User)-[r:RATED]->(m)
            WITH m, genres, avg(r.score) AS averageRating, count(r) AS ratingCount
            RETURN m.id AS id, m.title AS title, m.releaseDate AS releaseDate, m.releaseYear AS releaseYear,
                   genres, m.posterUrl AS posterUrl, averageRating, ratingCount,
                   EXISTS { (:User {id: $userId})-[:IN_WATCHLIST]->(m) } AS inWatchlist
            """;

    private final Neo4jClient neo4j;

    public RecommendationService(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    public List<Recommendation> recommend(String userId, Filters filters, String strategy, int limit) {
        int size = Math.clamp(limit, 1, 50);
        MovieFilter filter = MovieFilter.of(filters);
        List<Candidate> collaborative = List.of();
        List<Candidate> content = List.of();
        switch (strategy) {
            case "collaborative" -> collaborative = collaborative(userId, filter);
            case "content" -> content = content(userId, filter);
            case "popular" -> { }
            case "hybrid" -> {
                collaborative = collaborative(userId, filter);
                content = content(userId, filter);
            }
            default -> throw ApiException.badRequest("strategy must be hybrid, collaborative, content or popular");
        }
        List<Ranked> ranked = RecommendationRanker.rank(collaborative, content, popular(userId, filter), size);
        Map<String, Card> cards = cards(userId, ranked.stream().map(Ranked::movieId).toList());
        return ranked.stream()
                .filter(r -> cards.containsKey(r.movieId()))
                .map(r -> new Recommendation(cards.get(r.movieId()).movie(), r.score(), r.strategy(), r.reasons(),
                        cards.get(r.movieId()).inWatchlist()))
                .toList();
    }

    private List<Candidate> collaborative(String userId, MovieFilter filter) {
        return run(COLLABORATIVE, userId, filter, r -> {
            List<String> fans = r.get("fans").asList(Value::asString);
            String names = String.join(", ", fans.subList(0, Math.min(3, fans.size())));
            String more = fans.size() > 3 ? " and " + (fans.size() - 3) + " more" : "";
            return new Candidate(r.get("id").asString(), r.get("score").asDouble(),
                    "Loved by " + names + more + (fans.size() == 1 ? ", who shares" : ", who share") + " your taste");
        });
    }

    private List<Candidate> content(String userId, MovieFilter filter) {
        return run(CONTENT, userId, filter, r -> new Candidate(r.get("id").asString(), r.get("score").asDouble(),
                "Matches your taste in " + String.join(", ", r.get("genres").asList(Value::asString))));
    }

    private List<Candidate> popular(String userId, MovieFilter filter) {
        return run(POPULAR, userId, filter, r -> new Candidate(r.get("id").asString(), r.get("score").asDouble(),
                r.get("ratings").asLong() > 0 ? "Popular with Neo4flix members" : "New in the catalogue"));
    }

    private List<Candidate> run(String template, String userId, MovieFilter filter,
            Function<Record, Candidate> mapper) {
        Map<String, Object> params = new HashMap<>(filter.params());
        params.put("userId", userId);
        params.put("candidates", (long) CANDIDATES);
        Collection<Candidate> rows = neo4j.query(template.formatted(filter.condition()))
                .bindAll(params).fetchAs(Candidate.class).mappedBy((t, r) -> mapper.apply(r)).all();
        return List.copyOf(rows);
    }

    record Card(MovieCard movie, boolean inWatchlist) {
    }

    Map<String, Card> cards(String userId, List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return neo4j.query(CARDS).bindAll(Map.of("ids", ids, "userId", userId))
                .fetchAs(Card.class)
                .mappedBy((t, r) -> new Card(toCard(r), r.get("inWatchlist").asBoolean()))
                .all().stream()
                .collect(Collectors.toMap(c -> c.movie().id(), c -> c));
    }

    static MovieCard toCard(Record r) {
        Value avg = r.get("averageRating");
        return new MovieCard(r.get("id").asString(), r.get("title").asString(),
                r.get("releaseDate").isNull() ? null : r.get("releaseDate").asLocalDate(),
                r.get("releaseYear").isNull() ? null : r.get("releaseYear").asInt(),
                r.get("genres").asList(Value::asString),
                r.get("posterUrl").isNull() ? null : r.get("posterUrl").asString(),
                avg.isNull() ? null : Math.round(avg.asDouble() * 10) / 10.0, r.get("ratingCount").asLong());
    }
}
