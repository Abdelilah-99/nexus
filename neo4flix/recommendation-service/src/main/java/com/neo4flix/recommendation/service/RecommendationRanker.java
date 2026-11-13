package com.neo4flix.recommendation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure ranking logic (no database): merges the candidates of each algorithm into one ordered list.
 * Each list is first normalised to 0..1 (divided by its best score) so the weights are comparable.
 */
public final class RecommendationRanker {

    public static final double COLLABORATIVE_WEIGHT = 0.6;
    public static final double CONTENT_WEIGHT = 0.4;
    /** Popular movies only fill the remaining places, below every personalised result. */
    public static final double POPULAR_WEIGHT = 0.1;

    /** One candidate movie produced by one algorithm, with the reason to show to the user. */
    public record Candidate(String movieId, double score, String reason) {
    }

    public record Ranked(String movieId, double score, String strategy, List<String> reasons) {
    }

    private RecommendationRanker() {
    }

    public static List<Ranked> rank(List<Candidate> collaborative, List<Candidate> content, List<Candidate> popular,
            int limit) {
        Map<String, Double> cf = normalise(collaborative);
        Map<String, Double> cb = normalise(content);
        Map<String, Ranked> merged = new LinkedHashMap<>();
        for (Candidate c : collaborative) {
            merged.put(c.movieId(), new Ranked(c.movieId(), COLLABORATIVE_WEIGHT * cf.get(c.movieId()),
                    "collaborative", List.of(c.reason())));
        }
        for (Candidate c : content) {
            double contentScore = CONTENT_WEIGHT * cb.get(c.movieId());
            merged.merge(c.movieId(), new Ranked(c.movieId(), contentScore, "content", List.of(c.reason())),
                    (existing, added) -> {
                        List<String> reasons = new ArrayList<>(existing.reasons());
                        reasons.addAll(added.reasons());
                        return new Ranked(existing.movieId(), existing.score() + added.score(), "hybrid", reasons);
                    });
        }
        List<Ranked> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingDouble(Ranked::score).reversed());
        if (result.size() > limit) {
            return round(result.subList(0, limit));
        }
        Map<String, Double> pop = normalise(popular);
        for (Candidate c : popular) {
            if (result.size() >= limit) {
                break;
            }
            if (!merged.containsKey(c.movieId())) {
                result.add(new Ranked(c.movieId(), POPULAR_WEIGHT * pop.get(c.movieId()), "popular",
                        List.of(c.reason())));
            }
        }
        return round(result);
    }

    private static Map<String, Double> normalise(List<Candidate> candidates) {
        double max = candidates.stream().mapToDouble(Candidate::score).max().orElse(0);
        Map<String, Double> normalised = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            normalised.put(c.movieId(), max <= 0 ? 0.0 : c.score() / max);
        }
        return normalised;
    }

    private static List<Ranked> round(List<Ranked> ranked) {
        return ranked.stream()
                .map(r -> new Ranked(r.movieId(), Math.round(r.score() * 1000) / 1000.0, r.strategy(), r.reasons()))
                .toList();
    }
}
