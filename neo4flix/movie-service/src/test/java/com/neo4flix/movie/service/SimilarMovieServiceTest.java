package com.neo4flix.movie.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimilarMovieServiceTest {

    @Test
    void explainsWhyAMovieIsSimilar() {
        assertThat(SimilarMovieService.reasons(2, 3, true))
                .containsExactly("3 fans of this movie also loved it", "Same director", "2 genres in common");
        assertThat(SimilarMovieService.reasons(1, 1, false))
                .containsExactly("1 fan of this movie also loved it", "1 genre in common");
        assertThat(SimilarMovieService.reasons(0, 0, false)).isEmpty();
    }
}
