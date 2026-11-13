package com.neo4flix.rating.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.neo4flix.security.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MovieClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MovieClient client = new MovieClient(builder, "http://movie-service:8082");

    @Test
    void returnsTheMovieWhenItExists() {
        server.expect(requestTo("http://movie-service:8082/api/movies/up-2009"))
                .andRespond(withSuccess("{\"id\":\"up-2009\",\"title\":\"Up\",\"releaseYear\":2009,\"runtime\":96}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.get("up-2009").title()).isEqualTo("Up");
    }

    @Test
    void translatesA404IntoNotFound() {
        server.expect(requestTo("http://movie-service:8082/api/movies/nope")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.get("nope"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
