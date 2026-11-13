package com.neo4flix.rating.client;

import com.neo4flix.security.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * REST client for the movie-service. The rating-service never writes (:Movie) nodes: before rating a movie it
 * asks the service that owns movies whether it exists.
 */
@Component
public class MovieClient {

    public record MovieSummary(String id, String title, Integer releaseYear) {
    }

    private final RestClient rest;

    public MovieClient(RestClient.Builder builder, @Value("${neo4flix.movie-service-url}") String baseUrl) {
        this.rest = builder.baseUrl(baseUrl).build();
    }

    public MovieSummary get(String movieId) {
        try {
            MovieSummary movie = rest.get().uri("/api/movies/{id}", movieId).retrieve().body(MovieSummary.class);
            if (movie == null) {
                throw ApiException.notFound("Movie not found");
            }
            return movie;
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Movie not found");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Movie service is unavailable");
        }
    }
}
