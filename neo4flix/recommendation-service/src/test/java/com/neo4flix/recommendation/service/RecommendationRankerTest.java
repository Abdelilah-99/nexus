package com.neo4flix.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import com.neo4flix.recommendation.service.RecommendationRanker.Candidate;
import com.neo4flix.recommendation.service.RecommendationRanker.Ranked;
import org.junit.jupiter.api.Test;

class RecommendationRankerTest {

    @Test
    void aMovieFoundByBothAlgorithmsBecomesHybridAndRanksFirst() {
        List<Ranked> ranked = RecommendationRanker.rank(
                List.of(new Candidate("a", 4, "cf a"), new Candidate("b", 2, "cf b")),
                List.of(new Candidate("b", 10, "content b"), new Candidate("c", 5, "content c")),
                List.of(), 10);
        assertThat(ranked).extracting(Ranked::movieId).containsExactly("b", "a", "c");
        assertThat(ranked.get(0).strategy()).isEqualTo("hybrid");
        assertThat(ranked.get(0).reasons()).containsExactly("cf b", "content b");
        // b = 0.6 * 2/4 + 0.4 * 10/10 = 0.7 ; a = 0.6 * 1 = 0.6 ; c = 0.4 * 0.5 = 0.2
        assertThat(ranked).extracting(Ranked::score).containsExactly(0.7, 0.6, 0.2);
    }

    @Test
    void popularMoviesOnlyFillTheRemainingPlaces() {
        List<Ranked> ranked = RecommendationRanker.rank(
                List.of(new Candidate("a", 1, "cf")), List.of(),
                List.of(new Candidate("a", 9, "pop"), new Candidate("p1", 9, "pop"), new Candidate("p2", 3, "pop")),
                2);
        assertThat(ranked).extracting(Ranked::movieId).containsExactly("a", "p1");
        assertThat(ranked.get(1).strategy()).isEqualTo("popular");
    }

    @Test
    void coldStartUserGetsPopularMovies() {
        List<Ranked> ranked = RecommendationRanker.rank(List.of(), List.of(),
                List.of(new Candidate("p1", 4, "pop"), new Candidate("p2", 2, "pop")), 5);
        assertThat(ranked).extracting(Ranked::strategy).containsOnly("popular");
        assertThat(ranked).hasSize(2);
    }
}
