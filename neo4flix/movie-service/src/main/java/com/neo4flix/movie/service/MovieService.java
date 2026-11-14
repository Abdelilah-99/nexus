package com.neo4flix.movie.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.neo4flix.movie.web.MovieDtos.Genre;
import com.neo4flix.movie.web.MovieDtos.Movie;
import com.neo4flix.movie.web.MovieDtos.MovieRequest;
import com.neo4flix.movie.web.MovieDtos.Page;
import com.neo4flix.movie.web.MovieDtos.SearchCriteria;
import com.neo4flix.security.ApiException;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class MovieService {

    /**
     * Shared RETURN part: genres and rating statistics computed on the fly from the graph.
     * The average is never stored, so it can never be out of date.
     */
    static final String PROJECTION = """
            OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre)
            WITH m, collect(DISTINCT g.name) AS genres
            OPTIONAL MATCH (:User)-[r:RATED]->(m)
            WITH m, genres, avg(r.score) AS averageRating, count(r) AS ratingCount
            """;

    static final String RETURN_MOVIE = """
            RETURN m.id AS id, m.title AS title, m.releaseDate AS releaseDate, m.releaseYear AS releaseYear,
                   m.runtime AS runtime, m.director AS director, m.overview AS overview, m.posterUrl AS posterUrl,
                   genres, averageRating, ratingCount
            """;

    private static final Map<String, String> SORTS = Map.of(
            "title", "m.titleLower ASC",
            "year", "m.releaseDate DESC",
            "oldest", "m.releaseDate ASC",
            // coalesce: Cypher sorts null first in DESC order, which would put unrated movies on top.
            "rating", "coalesce(averageRating, 0) DESC, ratingCount DESC",
            "popular", "ratingCount DESC, coalesce(averageRating, 0) DESC");

    private final Neo4jClient neo4j;

    public MovieService(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    public Page<Movie> search(SearchCriteria c) {
        int size = Math.clamp(c.size(), 1, 100);
        int page = Math.max(c.page(), 0);
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        // The WHERE clause is assembled from fixed fragments; user input only ever goes into parameters.
        if (c.title() != null && !c.title().isBlank()) {
            where.add("m.titleLower CONTAINS $title");
            params.put("title", c.title().trim().toLowerCase());
        }
        if (c.genre() != null && !c.genre().isBlank()) {
            where.add("EXISTS { (m)-[:IN_GENRE]->(:Genre {name: $genre}) }");
            params.put("genre", c.genre().trim());
        }
        if (c.year() != null) {
            where.add("m.releaseYear = $year");
            params.put("year", c.year());
        }
        if (c.yearFrom() != null) {
            where.add("m.releaseYear >= $yearFrom");
            params.put("yearFrom", c.yearFrom());
        }
        if (c.yearTo() != null) {
            where.add("m.releaseYear <= $yearTo");
            params.put("yearTo", c.yearTo());
        }
        if (c.releasedAfter() != null) {
            where.add("m.releaseDate >= $releasedAfter");
            params.put("releasedAfter", c.releasedAfter());
        }
        if (c.releasedBefore() != null) {
            where.add("m.releaseDate <= $releasedBefore");
            params.put("releasedBefore", c.releasedBefore());
        }
        String match = "MATCH (m:Movie)" + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where)) + "\n";
        String ratingFilter = c.minRating() == null ? "" : "WITH * WHERE averageRating >= $minRating\n";
        if (c.minRating() != null) {
            params.put("minRating", c.minRating());
        }
        String order = SORTS.getOrDefault(c.sort() == null ? "title" : c.sort(), SORTS.get("title"));

        long total = neo4j.query(match + PROJECTION + ratingFilter + "RETURN count(m)")
                .bindAll(params).fetchAs(Long.class).one().orElse(0L);
        params.put("skip", (long) page * size);
        params.put("limit", (long) size);
        List<Movie> items = List.copyOf(neo4j.query(match + PROJECTION + ratingFilter + RETURN_MOVIE
                        + "ORDER BY " + order + ", m.titleLower ASC SKIP $skip LIMIT $limit")
                .bindAll(params).fetchAs(Movie.class).mappedBy((t, r) -> toMovie(r)).all());
        return new Page<>(items, total, page, size);
    }

    public Movie get(String id) {
        return neo4j.query("MATCH (m:Movie {id: $id})\n" + PROJECTION + RETURN_MOVIE)
                .bind(id).to("id").fetchAs(Movie.class).mappedBy((t, r) -> toMovie(r)).one()
                .orElseThrow(() -> ApiException.notFound("Movie not found"));
    }

    public List<Genre> genres() {
        return List.copyOf(neo4j.query("""
                        MATCH (g:Genre)
                        RETURN g.name AS name, COUNT { (g)<-[:IN_GENRE]-(:Movie) } AS movieCount
                        ORDER BY name
                        """)
                .fetchAs(Genre.class)
                .mappedBy((t, r) -> new Genre(r.get("name").asString(), r.get("movieCount").asLong()))
                .all());
    }

    public Movie create(MovieRequest request) {
        String id = slug(request.title(), request.releaseDate().getYear());
        boolean exists = neo4j.query("RETURN EXISTS { (:Movie {id: $id}) }").bind(id).to("id")
                .fetchAs(Boolean.class).one().orElse(false);
        if (exists) {
            throw ApiException.conflict("This movie already exists");
        }
        write(id, request, true);
        return get(id);
    }

    public Movie update(String id, MovieRequest request) {
        get(id);
        write(id, request, false);
        return get(id);
    }

    /** DETACH DELETE also removes the ratings, watchlist entries and genre links of the movie. */
    public void delete(String id) {
        get(id);
        neo4j.query("MATCH (m:Movie {id: $id}) DETACH DELETE m").bind(id).to("id").run();
        neo4j.query("MATCH (g:Genre) WHERE NOT (g)<-[:IN_GENRE]-() DELETE g").run();
    }

    private void write(String id, MovieRequest request, boolean create) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("title", request.title().trim());
        params.put("releaseDate", request.releaseDate());
        params.put("runtime", request.runtime());
        params.put("director", request.director());
        params.put("overview", request.overview());
        params.put("posterUrl", request.posterUrl());
        params.put("genres", request.genres().stream().map(String::trim).distinct().toList());
        params.put("create", create);
        // Replace the genre links: remove the old IN_GENRE relationships, then MERGE the new ones.
        neo4j.query("""
                MERGE (m:Movie {id: $id})
                ON CREATE SET m.createdAt = datetime()
                SET m.title = $title, m.titleLower = toLower($title), m.releaseDate = $releaseDate,
                    m.releaseYear = $releaseDate.year, m.runtime = $runtime, m.director = $director,
                    m.overview = $overview, m.posterUrl = $posterUrl
                WITH m
                OPTIONAL MATCH (m)-[old:IN_GENRE]->(:Genre)
                DELETE old
                WITH DISTINCT m
                UNWIND $genres AS genreName
                MERGE (g:Genre {name: genreName})
                MERGE (m)-[:IN_GENRE]->(g)
                """).bindAll(params).run();
        neo4j.query("MATCH (g:Genre) WHERE NOT (g)<-[:IN_GENRE]-() DELETE g").run();
    }

    static Movie toMovie(Record r) {
        Value date = r.get("releaseDate");
        Value avg = r.get("averageRating");
        return new Movie(r.get("id").asString(), r.get("title").asString(),
                date.isNull() ? null : date.asLocalDate(),
                r.get("releaseYear").isNull() ? null : r.get("releaseYear").asInt(),
                r.get("runtime").isNull() ? null : r.get("runtime").asInt(),
                nullable(r.get("director")), nullable(r.get("overview")), nullable(r.get("posterUrl")),
                r.get("genres").asList(Value::asString),
                avg.isNull() ? null : Math.round(avg.asDouble() * 10) / 10.0,
                r.get("ratingCount").asLong());
    }

    private static String nullable(Value value) {
        return value.isNull() ? null : value.asString();
    }

    /** "The Matrix", 1999 → "the-matrix-1999": readable, stable ids that also make nice URLs. */
    public static String slug(String title, int year) {
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String base = ascii.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base + "-" + year;
    }

    public static Map<String, Object> withSlugId(Map<String, Object> movie) {
        Map<String, Object> copy = new HashMap<>(movie);
        copy.put("id", slug((String) movie.get("title"), Integer.parseInt(((String) movie.get("releaseDate")).substring(0, 4))));
        return copy;
    }
}
