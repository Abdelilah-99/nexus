package com.neo4flix.movie.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MovieServiceTest {

    @Test
    void slugIsReadableAndStable() {
        assertThat(MovieService.slug("The Matrix", 1999)).isEqualTo("the-matrix-1999");
        assertThat(MovieService.slug("Amélie", 2001)).isEqualTo("amelie-2001");
        assertThat(MovieService.slug("  Spider-Man: Into the Spider-Verse ", 2018))
                .isEqualTo("spider-man-into-the-spider-verse-2018");
    }

    @Test
    void seedRowsGetTheirSlugId() {
        assertThat(MovieService.withSlugId(Map.of("title", "Up", "releaseDate", "2009-05-29")))
                .containsEntry("id", "up-2009");
    }
}
