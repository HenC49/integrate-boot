package com.github.henc.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying the global exception handler end-to-end: every failure kind
 * is rendered into the shared {@code ResultInfo} envelope with a semantically correct
 * HTTP status, and internal details of unexpected failures never reach the client.
 *
 * <p>Runs on its own H2 in-memory database: {@code @AutoConfigureMockMvc} gives this test
 * a separate application context, and the shared {@code mem:testdb} (with
 * {@code sql.init.mode=always}) would accumulate the seed rows a second time and break the
 * data assertions of the other ITs.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:errordb;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
class ErrorHandlingIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessExceptionRespondsHttp200WithFailureEnvelope() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/errors/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("insufficient balance"));
    }

    @Test
    void notFoundExceptionResponds404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("order 1 not found"));
    }

    @Test
    void conflictExceptionKeepsExplicitBusinessCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(10003))
                .andExpect(jsonPath("$.message").value("duplicate order id"));
    }

    @Test
    void validationFailureResponds400WithFieldDetails() throws Exception {
        // Field errors are joined in no guaranteed order — assert each one is present.
        mockMvc.perform(MockMvcRequestBuilders.post("/errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"age\":200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("name: must not be blank")))
                .andExpect(jsonPath("$.message").value(containsString("age: must be less than or equal to 150")));
    }

    @Test
    void typeMismatchResponds400() throws Exception {
        // /users/{id} declares a Long path variable; "abc" cannot bind.
        mockMvc.perform(MockMvcRequestBuilders.get("/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid value for parameter 'id'"));
    }

    @Test
    void unsupportedMethodResponds405() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/errors/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void unmatchedPathResponds404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("resource not found"));
    }

    @Test
    void unexpectedExceptionRespondsGeneric500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("internal server error"));
    }
}
