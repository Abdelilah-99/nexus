package com.neo4flix.recommendation.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.neo4flix.recommendation.web.RecommendationDtos.Filters;

/** Turns the optional filters into a Cypher condition on the variable "rec", with bound parameters. */
record MovieFilter(String condition, Map<String, Object> params) {

    static MovieFilter of(Filters f) {
        List<String> parts = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (f.genre() != null && !f.genre().isBlank()) {
            parts.add("EXISTS { (rec)-[:IN_GENRE]->(:Genre {name: $genre}) }");
            params.put("genre", f.genre().trim());
        }
        if (f.yearFrom() != null) {
            parts.add("rec.releaseYear >= $yearFrom");
            params.put("yearFrom", f.yearFrom());
        }
        if (f.yearTo() != null) {
            parts.add("rec.releaseYear <= $yearTo");
            params.put("yearTo", f.yearTo());
        }
        if (f.releasedAfter() != null) {
            parts.add("rec.releaseDate >= $releasedAfter");
            params.put("releasedAfter", f.releasedAfter());
        }
        if (f.releasedBefore() != null) {
            parts.add("rec.releaseDate <= $releasedBefore");
            params.put("releasedBefore", f.releasedBefore());
        }
        return new MovieFilter(parts.isEmpty() ? "" : " AND " + String.join(" AND ", parts), params);
    }
}
