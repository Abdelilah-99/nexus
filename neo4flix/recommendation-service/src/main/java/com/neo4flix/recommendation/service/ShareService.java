package com.neo4flix.recommendation.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.neo4flix.recommendation.client.ServiceClients;
import com.neo4flix.recommendation.web.RecommendationDtos.MovieCard;
import com.neo4flix.recommendation.web.RecommendationDtos.Share;
import com.neo4flix.security.ApiException;
import org.neo4j.driver.Record;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Sharing a movie with a friend creates a (:Share) node, because the share has its own data (message, date, read):
 * (sender:User)-[:SHARED]->(s:Share)-[:WITH]->(friend:User), (s)-[:ABOUT]->(:Movie).
 */
@Service
public class ShareService {

    private static final String RETURN_SHARE = """
            OPTIONAL MATCH (m)-[:IN_GENRE]->(g:Genre)
            WITH s, sender, friend, m, collect(g.name) AS genres
            OPTIONAL MATCH (:User)-[r:RATED]->(m)
            WITH s, sender, friend, m, genres, avg(r.score) AS averageRating, count(r) AS ratingCount
            RETURN s.id AS shareId, sender.id AS fromUserId, sender.username AS fromUsername,
                   friend.id AS toUserId, friend.username AS toUsername, s.message AS message,
                   s.sharedAt AS sharedAt, s.read AS read,
                   m.id AS id, m.title AS title, m.releaseDate AS releaseDate, m.releaseYear AS releaseYear,
                   genres, m.posterUrl AS posterUrl, averageRating, ratingCount
            ORDER BY s.sharedAt DESC
            """;

    private final Neo4jClient neo4j;
    private final ServiceClients clients;

    public ShareService(Neo4jClient neo4j, ServiceClients clients) {
        this.neo4j = neo4j;
        this.clients = clients;
    }

    public Share share(String userId, String movieId, String friendId, String message) {
        clients.movie(movieId);
        if (!clients.isFriend(friendId)) {
            throw ApiException.forbidden("You can only share movies with your friends");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("friendId", friendId);
        params.put("movieId", movieId);
        params.put("message", message == null || message.isBlank() ? null : message.trim());
        return neo4j.query("""
                        MATCH (sender:User {id: $userId}), (friend:User {id: $friendId}), (m:Movie {id: $movieId})
                        CREATE (sender)-[:SHARED]->(s:Share {id: randomUUID(), message: $message,
                                                             sharedAt: datetime(), read: false})-[:WITH]->(friend),
                               (s)-[:ABOUT]->(m)
                        WITH s, sender, friend, m
                        """ + RETURN_SHARE)
                .bindAll(params).fetchAs(Share.class).mappedBy((t, r) -> toShare(r)).one()
                .orElseThrow(() -> ApiException.notFound("User or movie not found"));
    }

    public List<Share> inbox(String userId) {
        return List.copyOf(neo4j.query("""
                        MATCH (sender:User)-[:SHARED]->(s:Share)-[:WITH]->(friend:User {id: $userId}),
                              (s)-[:ABOUT]->(m:Movie)
                        WITH s, sender, friend, m ORDER BY s.sharedAt DESC LIMIT 100
                        """ + RETURN_SHARE)
                .bind(userId).to("userId").fetchAs(Share.class).mappedBy((t, r) -> toShare(r)).all());
    }

    public List<Share> sent(String userId) {
        return List.copyOf(neo4j.query("""
                        MATCH (sender:User {id: $userId})-[:SHARED]->(s:Share)-[:WITH]->(friend:User),
                              (s)-[:ABOUT]->(m:Movie)
                        WITH s, sender, friend, m ORDER BY s.sharedAt DESC LIMIT 100
                        """ + RETURN_SHARE)
                .bind(userId).to("userId").fetchAs(Share.class).mappedBy((t, r) -> toShare(r)).all());
    }

    public long unreadCount(String userId) {
        return neo4j.query("MATCH (:User)-[:SHARED]->(s:Share {read: false})-[:WITH]->(:User {id: $userId}) "
                        + "RETURN count(s)")
                .bind(userId).to("userId").fetchAs(Long.class).one().orElse(0L);
    }

    /** Only the recipient can mark a share as read. */
    public void markRead(String userId, String shareId) {
        long updated = neo4j.query("""
                        MATCH (s:Share {id: $shareId})-[:WITH]->(:User {id: $userId})
                        SET s.read = true RETURN count(s)
                        """)
                .bindAll(Map.of("shareId", shareId, "userId", userId)).fetchAs(Long.class).one().orElse(0L);
        if (updated == 0) {
            throw ApiException.notFound("Share not found");
        }
    }

    /** The sender or the recipient can delete it. */
    public void delete(String userId, String shareId) {
        long deleted = neo4j.query("""
                        MATCH (s:Share {id: $shareId})
                        WHERE EXISTS { (:User {id: $userId})-[:SHARED]->(s) } OR EXISTS { (s)-[:WITH]->(:User {id: $userId}) }
                        DETACH DELETE s
                        RETURN count(*)
                        """)
                .bindAll(Map.of("shareId", shareId, "userId", userId)).fetchAs(Long.class).one().orElse(0L);
        if (deleted == 0) {
            throw ApiException.notFound("Share not found");
        }
    }

    private static Share toShare(Record r) {
        MovieCard movie = RecommendationService.toCard(r);
        return new Share(r.get("shareId").asString(), r.get("fromUserId").asString(), r.get("fromUsername").asString(),
                r.get("toUserId").asString(), r.get("toUsername").asString(), movie,
                r.get("message").isNull() ? null : r.get("message").asString(),
                r.get("sharedAt").asZonedDateTime().toInstant(), r.get("read").asBoolean());
    }
}
