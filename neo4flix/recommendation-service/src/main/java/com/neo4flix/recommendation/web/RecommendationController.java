package com.neo4flix.recommendation.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.neo4flix.recommendation.service.RecommendationService;
import com.neo4flix.recommendation.service.ShareService;
import com.neo4flix.recommendation.web.RecommendationDtos.Filters;
import com.neo4flix.recommendation.web.RecommendationDtos.Recommendation;
import com.neo4flix.recommendation.web.RecommendationDtos.Share;
import com.neo4flix.recommendation.web.RecommendationDtos.ShareRequest;
import com.neo4flix.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendations;
    private final ShareService shares;

    public RecommendationController(RecommendationService recommendations, ShareService shares) {
        this.recommendations = recommendations;
        this.shares = shares;
    }

    /** Example: /api/recommendations?genre=Sci-Fi&yearFrom=2000&strategy=hybrid&limit=20 */
    @GetMapping
    public List<Recommendation> recommend(@RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) LocalDate releasedAfter,
            @RequestParam(required = false) LocalDate releasedBefore,
            @RequestParam(defaultValue = "hybrid") String strategy,
            @RequestParam(defaultValue = "20") int limit) {
        return recommendations.recommend(CurrentUser.id(),
                new Filters(genre, yearFrom, yearTo, releasedAfter, releasedBefore), strategy, limit);
    }

    @PostMapping("/share")
    @ResponseStatus(HttpStatus.CREATED)
    public Share share(@Valid @RequestBody ShareRequest request) {
        return shares.share(CurrentUser.id(), request.movieId(), request.friendId(), request.message());
    }

    @GetMapping("/shared/inbox")
    public List<Share> inbox() {
        return shares.inbox(CurrentUser.id());
    }

    @GetMapping("/shared/sent")
    public List<Share> sent() {
        return shares.sent(CurrentUser.id());
    }

    @GetMapping("/shared/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", shares.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/shared/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String id) {
        shares.markRead(CurrentUser.id(), id);
    }

    @DeleteMapping("/shared/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        shares.delete(CurrentUser.id(), id);
    }
}
