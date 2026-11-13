package com.neo4flix.rating.web;

import java.util.List;
import com.neo4flix.rating.service.RatingService;
import com.neo4flix.rating.web.RatingDtos.CreateRatingRequest;
import com.neo4flix.rating.web.RatingDtos.MovieRatings;
import com.neo4flix.rating.web.RatingDtos.RateRequest;
import com.neo4flix.rating.web.RatingDtos.Rating;
import com.neo4flix.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RatingController {

    private final RatingService ratings;

    public RatingController(RatingService ratings) {
        this.ratings = ratings;
    }

    @PostMapping("/api/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public Rating create(@Valid @RequestBody CreateRatingRequest request) {
        return ratings.create(CurrentUser.id(), request.movieId(), request.score(), request.review());
    }

    @PutMapping("/api/ratings/{movieId}")
    public Rating rate(@PathVariable String movieId, @Valid @RequestBody RateRequest request) {
        return ratings.rate(CurrentUser.id(), movieId, request.score(), request.review());
    }

    @GetMapping("/api/ratings/me")
    public List<Rating> mine() {
        return ratings.byUser(CurrentUser.id());
    }

    @GetMapping("/api/ratings/me/{movieId}")
    public Rating mine(@PathVariable String movieId) {
        return ratings.mine(CurrentUser.id(), movieId);
    }

    @DeleteMapping("/api/ratings/{movieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String movieId) {
        ratings.delete(CurrentUser.id(), movieId);
    }

    @GetMapping("/api/ratings/movie/{movieId}")
    public MovieRatings forMovie(@PathVariable String movieId, @RequestParam(defaultValue = "20") int limit) {
        return ratings.byMovie(movieId, limit);
    }

    @GetMapping("/api/ratings/user/{userId}")
    public List<Rating> forUser(@PathVariable String userId) {
        return ratings.byUser(userId);
    }

    /** Moderation: an admin can remove an abusive review. */
    @DeleteMapping("/api/admin/ratings/{movieId}/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moderate(@PathVariable String movieId, @PathVariable String userId) {
        ratings.delete(userId, movieId);
    }
}
