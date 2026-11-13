package com.neo4flix.movie.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.neo4flix.movie.web.MovieDtos.SimilarMovie;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * "Because you looked at X": movies related to one movie, found by graph traversal. The score mixes:
 * <ul>
 *   <li>content: genres shared through (:Genre) nodes (Jaccard similarity) and the same director;</li>
 *   <li>collaborative: users who liked this movie (4-5 stars) and also liked the other one.</li>
 * </ul>
 */
@Service
public class SimilarMovieService {

    static final String SIMILAR = """
            MATCH (m:Movie {id: $id})
            CALL (m) {
              MATCH (m)-[:IN_GENRE]->(:Genre)<-[:IN_GENRE]-(c:Movie) WHERE c <> m RETURN DISTINCT c
              UNION
              MATCH (m)<-[r1:RATED]-(:User)-[r2:RATED]->(c:Movie)
              WHERE c <> m AND r1.score >= 4 AND r2.score >= 4
              RETURN DISTINCT c
            }
            WITH m, c,
                 COUNT { (m)-[:IN_GENRE]->(:Genre)<-[:IN_GENRE]-(c) } AS sharedGenres,
                 COUNT { (m)-[:IN_GENRE]->(:Genre) } + COUNT { (c)-[:IN_GENRE]->(:Genre) } AS genreTotal,
                 COUNT { MATCH (m)<-[r1:RATED]-(:User)-[r2:RATED]->(c) WHERE r1.score >= 4 AND r2.score >= 4 }
                   AS coLikers
            WITH c, sharedGenres, coLikers,
                 CASE WHEN genreTotal = sharedGenres THEN 0.0
                      ELSE toFloat(sharedGenres) / (genreTotal - sharedGenres) END AS genreSimilarity,
                 m.director IS NOT NULL AND m.director = c.director AS sameDirector
            WITH c, sharedGenres, coLikers, sameDirector,
                 genreSimilarity * 2.0 + CASE WHEN sameDirector THEN 1.0 ELSE 0.0 END + log(1 + coLikers) * 1.5
                   AS score
            ORDER BY score DESC, c.titleLower
            LIMIT $limit
            OPTIONAL MATCH (c)-[:IN_GENRE]->(g:Genre)
            WITH c, sharedGenres, coLikers, sameDirector, score, collect(g.name) AS genres
            OPTIONAL MATCH (:User)-[r:RATED]->(c)
            WITH c AS m, sharedGenres, coLikers, sameDirector, score, genres,
                 avg(r.score) AS averageRating, count(r) AS ratingCount
            """ + MovieService.RETURN_MOVIE + """
            , score, sharedGenres, coLikers, sameDirector
            ORDER BY score DESC, m.titleLower
            """;

    private final Neo4jClient neo4j;
    private final MovieService movies;

    public SimilarMovieService(Neo4jClient neo4j, MovieService movies) {
        this.neo4j = neo4j;
        this.movies = movies;
    }

    public List<SimilarMovie> similar(String movieId, int limit) {
        movies.get(movieId);
        return List.copyOf(neo4j.query(SIMILAR)
                .bindAll(Map.of("id", movieId, "limit", (long) Math.clamp(limit, 1, 50)))
                .fetchAs(SimilarMovie.class)
                .mappedBy((types, r) -> {
                    long sharedGenres = r.get("sharedGenres").asLong();
                    long coLikers = r.get("coLikers").asLong();
                    boolean sameDirector = r.get("sameDirector").asBoolean();
                    return new SimilarMovie(MovieService.toMovie(r),
                            Math.round(r.get("score").asDouble() * 100) / 100.0,
                            reasons(sharedGenres, coLikers, sameDirector));
                })
                .all());
    }

    static List<String> reasons(long sharedGenres, long coLikers, boolean sameDirector) {
        List<String> reasons = new ArrayList<>();
        if (coLikers > 0) {
            reasons.add(coLikers + (coLikers == 1 ? " fan" : " fans") + " of this movie also loved it");
        }
        if (sameDirector) {
            reasons.add("Same director");
        }
        if (sharedGenres > 0) {
            reasons.add(sharedGenres + (sharedGenres == 1 ? " genre" : " genres") + " in common");
        }
        return reasons;
    }
}
