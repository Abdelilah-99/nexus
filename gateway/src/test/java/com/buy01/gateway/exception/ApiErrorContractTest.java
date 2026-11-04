package com.buy01.gateway.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorContractTest {
    @Test
    void routingAndUnexpectedErrorsUseSafeContract() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Failures()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/contract/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.details").isMap());
        mvc.perform(get("/contract/upstream")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
                .andExpect(content().string(not(containsString("private-secret"))));
        mvc.perform(get("/contract/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @RestController
    static class Failures {
        @GetMapping("/contract/{kind}")
        Object fail(@PathVariable String kind) {
            if ("missing".equals(kind)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if ("upstream".equals(kind)) throw new RestClientException("private-secret");
            throw new IllegalStateException("private-secret");
        }
    }
}
