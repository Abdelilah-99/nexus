package com.neo4flix.movie.config;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import com.neo4flix.movie.service.MovieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.neo4j.core.Neo4jClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class MovieSchemaAndSeed {

    private static final Logger log = LoggerFactory.getLogger(MovieSchemaAndSeed.class);

    @Bean
    ApplicationRunner movieSchema(Neo4jClient neo4j, ObjectMapper json,
            @Value("${neo4flix.seed-movies:true}") boolean seed) {
        return args -> {
            neo4j.query("CREATE CONSTRAINT movie_id IF NOT EXISTS FOR (m:Movie) REQUIRE m.id IS UNIQUE").run();
            neo4j.query("CREATE CONSTRAINT genre_name IF NOT EXISTS FOR (g:Genre) REQUIRE g.name IS UNIQUE").run();
            neo4j.query("CREATE INDEX movie_year IF NOT EXISTS FOR (m:Movie) ON (m.releaseYear)").run();
            neo4j.query("CREATE INDEX movie_title IF NOT EXISTS FOR (m:Movie) ON (m.titleLower)").run();
            if (!seed) {
                return;
            }
            long count = neo4j.query("MATCH (m:Movie) RETURN count(m)").fetchAs(Long.class).one().orElse(0L);
            if (count > 0) {
                return;
            }
            List<Map<String, Object>> movies;
            try (InputStream in = new ClassPathResource("seed/movies.json").getInputStream()) {
                movies = json.readValue(in, new TypeReference<>() { });
            }
            // One query for the whole file: UNWIND turns the list into rows, one per movie.
            neo4j.query("""
                    UNWIND $movies AS mv
                    MERGE (m:Movie {id: mv.id})
                    SET m.title = mv.title, m.titleLower = toLower(mv.title),
                        m.releaseDate = date(mv.releaseDate), m.releaseYear = date(mv.releaseDate).year,
                        m.runtime = mv.runtime, m.director = mv.director, m.overview = mv.overview,
                        m.posterUrl = mv.posterUrl, m.createdAt = datetime()
                    WITH m, mv
                    UNWIND mv.genres AS genreName
                    MERGE (g:Genre {name: genreName})
                    MERGE (m)-[:IN_GENRE]->(g)
                    """)
                    .bind(movies.stream().map(MovieService::withSlugId).toList()).to("movies")
                    .run();
            log.info("Seeded {} movies", movies.size());
        };
    }
}
