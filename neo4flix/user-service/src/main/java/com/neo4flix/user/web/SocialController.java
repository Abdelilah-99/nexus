package com.neo4flix.user.web;

import java.util.List;
import java.util.Map;
import com.neo4flix.security.CurrentUser;
import com.neo4flix.user.service.SocialService;
import com.neo4flix.user.web.SocialDtos.Friend;
import com.neo4flix.user.web.SocialDtos.WatchlistItem;
import com.neo4flix.user.web.UserDto.PublicUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SocialController {

    private final SocialService social;

    public SocialController(SocialService social) {
        this.social = social;
    }

    @GetMapping("/api/users/me/watchlist")
    public List<WatchlistItem> watchlist() {
        return social.watchlist(CurrentUser.id());
    }

    @PutMapping("/api/users/me/watchlist/{movieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addToWatchlist(@PathVariable String movieId) {
        social.addToWatchlist(CurrentUser.id(), movieId);
    }

    @DeleteMapping("/api/users/me/watchlist/{movieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromWatchlist(@PathVariable String movieId) {
        social.removeFromWatchlist(CurrentUser.id(), movieId);
    }

    @GetMapping("/api/users/search")
    public List<PublicUser> search(@RequestParam("q") String query) {
        return social.search(CurrentUser.id(), query);
    }

    @GetMapping("/api/users/me/friends")
    public List<Friend> friends() {
        return social.friends(CurrentUser.id());
    }

    @PutMapping("/api/users/me/friends/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFriend(@PathVariable String userId) {
        social.addFriend(CurrentUser.id(), userId);
    }

    @DeleteMapping("/api/users/me/friends/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(@PathVariable String userId) {
        social.removeFriend(CurrentUser.id(), userId);
    }

    /** Used over REST by the recommendation-service before it lets someone share a movie with a friend. */
    @GetMapping("/api/users/me/friends/{userId}")
    public Map<String, Boolean> isFriend(@PathVariable String userId) {
        return Map.of("friend", social.areFriends(CurrentUser.id(), userId));
    }
}
