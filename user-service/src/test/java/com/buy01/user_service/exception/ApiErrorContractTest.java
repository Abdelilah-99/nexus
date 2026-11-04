package com.buy01.user_service.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorContractTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Failures()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"400,BAD_REQUEST", "401,UNAUTHORIZED", "403,FORBIDDEN", "404,NOT_FOUND", "409,CONFLICT", "502,BAD_GATEWAY"})
    void frameworkFailuresHaveTheSharedContract(int status, String code) throws Exception {
        mvc.perform(get("/contract/" + status)).andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(content().string(not(containsString("private-secret"))));
    }

    @Test
    void fieldErrorsExposeMessagesWithoutRejectedValues() throws Exception {
        mvc.perform(get("/contract/validation")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.email").value("Must be a valid email"))
                .andExpect(jsonPath("$.details.quantity").value("Invalid value"))
                .andExpect(content().string(not(containsString("private-rejected-value"))));
    }

    @Test
    void malformedJsonIsBadRequestAndUnexpectedErrorsAreSafe() throws Exception {
        mvc.perform(get("/contract/query").param("page", "private-rejected-value"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/contract/body").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/contract/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(content().string(not(containsString("private-secret"))));
    }

    @Test
    void domainPermissionFailuresAndDuplicateAccountsUse403And409() throws Exception {
        mvc.perform(get("/contract/forbidden")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/contract/duplicate")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("private-secret"))));
    }

    @RestController
    static class Failures {
        @GetMapping("/contract/{kind}")
        Object fail(@PathVariable String kind) throws MethodArgumentNotValidException, NoSuchMethodException {
            if ("forbidden".equals(kind)) throw new ForbiddenException("Access denied");
            if ("duplicate".equals(kind)) throw new org.springframework.dao.DuplicateKeyException("private-secret");
            if ("validation".equals(kind)) {
                var binding = new BeanPropertyBindingResult(new Object(), "form");
                binding.addError(new FieldError("form", "email", "private-rejected-value", false, null, null, "Must be a valid email"));
                binding.addError(new FieldError("form", "quantity", "private-rejected-value", true, null, null, "Cannot convert private-rejected-value"));
                throw new MethodArgumentNotValidException(new MethodParameter(Failures.class.getDeclaredMethod("body", Map.class), 0), binding);
            }
            if ("unexpected".equals(kind)) throw new IllegalStateException("private-secret");
            throw new ResponseStatusException(HttpStatus.valueOf(Integer.parseInt(kind)), "private-secret");
        }

        @GetMapping("/contract/query")
        Object query(@org.springframework.web.bind.annotation.RequestParam int page) { return page; }

        @PostMapping("/contract/body")
        Object body(@RequestBody Map<String, Object> body) { return body; }
    }
}
