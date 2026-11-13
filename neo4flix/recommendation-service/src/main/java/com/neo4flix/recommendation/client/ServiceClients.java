package com.neo4flix.recommendation.client;

import java.util.Map;
import com.neo4flix.security.ApiException;
import com.neo4flix.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** REST calls to the services that own users and movies. The caller's JWT is forwarded. */
@Component
public class ServiceClients {

    public record MovieSummary(String id, String title, Integer releaseYear) {
    }

    private final RestClient users;
    private final RestClient movies;

    public ServiceClients(RestClient.Builder builder, @Value("${neo4flix.user-service-url}") String userUrl,
            @Value("${neo4flix.movie-service-url}") String movieUrl) {
        this.users = builder.clone().baseUrl(userUrl).build();
        this.movies = builder.clone().baseUrl(movieUrl).build();
    }

    /** Asks the user-service (which owns FRIEND relationships) whether the current user is friends with friendId. */
    public boolean isFriend(String friendId) {
        try {
            Map<String, Boolean> body = users.get().uri("/api/users/me/friends/{id}", friendId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + CurrentUser.bearerToken())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            return body != null && Boolean.TRUE.equals(body.get("friend"));
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "User service is unavailable");
        }
    }

    public MovieSummary movie(String movieId) {
        try {
            MovieSummary movie = movies.get().uri("/api/movies/{id}", movieId).retrieve().body(MovieSummary.class);
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
