package com.neo4flix.user.web;

import java.time.Instant;
import java.util.List;

public final class SocialDtos {

    private SocialDtos() {
    }

    /** A movie of the watchlist, read from the (:Movie) node the movie-service owns. */
    public record WatchlistItem(String movieId, String title, Integer releaseYear, List<String> genres,
            String posterUrl, Instant addedAt) {
    }

    public record Friend(String id, String username, String displayName, Instant since) {
    }
}
