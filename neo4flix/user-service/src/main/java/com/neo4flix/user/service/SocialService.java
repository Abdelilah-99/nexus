package com.neo4flix.user.service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.neo4flix.security.ApiException;
import com.neo4flix.user.web.SocialDtos.Friend;
import com.neo4flix.user.web.SocialDtos.WatchlistItem;
import com.neo4flix.user.web.UserDto.PublicUser;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Watchlist and friends are pure relationships, so they are written with Cypher:
 * (:User)-[:IN_WATCHLIST {addedAt}]->(:Movie) and (:User)-[:FRIEND {since}]-(:User).
 */
@Service
public class SocialService {

    private final Neo4jClient neo4j;

    public SocialService(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    public List<WatchlistItem> watchlist(String userId) {
        return List.copyOf(neo4j.query("""
                        MATCH (:User {id: $userId})-[w:IN_WATCHLIST]->(m:Movie)
                        OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre)
                        WITH m, w, collect(g.name) AS genres
                        RETURN m.id AS movieId, m.title AS title, m.releaseYear AS releaseYear, genres,
                               m.posterUrl AS posterUrl, w.addedAt AS addedAt
                        ORDER BY w.addedAt DESC
                        """)
                .bind(userId).to("userId")
                .fetchAs(WatchlistItem.class)
                .mappedBy((types, r) -> new WatchlistItem(r.get("movieId").asString(), r.get("title").asString(),
                        nullableInt(r.get("releaseYear")), r.get("genres").asList(Value::asString),
                        nullableString(r.get("posterUrl")), toInstant(r.get("addedAt"))))
                .all());
    }

    /** MERGE makes it idempotent: adding twice keeps a single relationship. */
    public void addToWatchlist(String userId, String movieId) {
        boolean found = neo4j.query("""
                        MATCH (u:User {id: $userId}), (m:Movie {id: $movieId})
                        MERGE (u)-[w:IN_WATCHLIST]->(m)
                        ON CREATE SET w.addedAt = datetime()
                        RETURN count(w) > 0 AS ok
                        """)
                .bindAll(Map.of("userId", userId, "movieId", movieId))
                .fetchAs(Boolean.class).one().orElse(false);
        if (!found) {
            throw ApiException.notFound("Movie not found");
        }
    }

    public void removeFromWatchlist(String userId, String movieId) {
        neo4j.query("MATCH (:User {id: $userId})-[w:IN_WATCHLIST]->(:Movie {id: $movieId}) DELETE w")
                .bindAll(Map.of("userId", userId, "movieId", movieId)).run();
    }

    public List<PublicUser> search(String userId, String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        Collection<PublicUser> found = neo4j.query("""
                        MATCH (u:User)
                        WHERE u.id <> $userId
                          AND (u.username CONTAINS $q OR toLower(u.displayName) CONTAINS $q)
                        RETURN u.id AS id, u.username AS username, u.displayName AS displayName, u.bio AS bio
                        ORDER BY u.username LIMIT 20
                        """)
                .bindAll(Map.of("userId", userId, "q", query.trim().toLowerCase()))
                .fetchAs(PublicUser.class)
                .mappedBy((types, r) -> new PublicUser(r.get("id").asString(), r.get("username").asString(),
                        nullableString(r.get("displayName")), nullableString(r.get("bio"))))
                .all();
        return List.copyOf(found);
    }

    public List<Friend> friends(String userId) {
        return List.copyOf(neo4j.query("""
                        MATCH (:User {id: $userId})-[f:FRIEND]-(friend:User)
                        RETURN friend.id AS id, friend.username AS username, friend.displayName AS displayName,
                               f.since AS since
                        ORDER BY friend.username
                        """)
                .bind(userId).to("userId")
                .fetchAs(Friend.class)
                .mappedBy((types, r) -> new Friend(r.get("id").asString(), r.get("username").asString(),
                        nullableString(r.get("displayName")), toInstant(r.get("since"))))
                .all());
    }

    /** Friendship is symmetric: the undirected pattern -[:FRIEND]- matches it from both sides. */
    public void addFriend(String userId, String friendId) {
        if (userId.equals(friendId)) {
            throw ApiException.badRequest("You cannot add yourself");
        }
        boolean found = neo4j.query("""
                        MATCH (me:User {id: $userId}), (other:User {id: $friendId})
                        MERGE (me)-[f:FRIEND]-(other)
                        ON CREATE SET f.since = datetime()
                        RETURN count(f) > 0 AS ok
                        """)
                .bindAll(Map.of("userId", userId, "friendId", friendId))
                .fetchAs(Boolean.class).one().orElse(false);
        if (!found) {
            throw ApiException.notFound("User not found");
        }
    }

    public void removeFriend(String userId, String friendId) {
        neo4j.query("MATCH (:User {id: $userId})-[f:FRIEND]-(:User {id: $friendId}) DELETE f")
                .bindAll(Map.of("userId", userId, "friendId", friendId)).run();
    }

    public boolean areFriends(String userId, String otherId) {
        return neo4j.query("RETURN EXISTS { (:User {id: $userId})-[:FRIEND]-(:User {id: $otherId}) } AS friends")
                .bindAll(Map.of("userId", userId, "otherId", otherId))
                .fetchAs(Boolean.class).one().orElse(false);
    }

    private static String nullableString(Value value) {
        return value.isNull() ? null : value.asString();
    }

    private static Integer nullableInt(Value value) {
        return value.isNull() ? null : value.asInt();
    }

    private static Instant toInstant(Value value) {
        if (value.isNull()) {
            return null;
        }
        ZonedDateTime time = value.asZonedDateTime();
        return time.toInstant();
    }
}
