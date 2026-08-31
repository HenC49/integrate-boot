package com.github.henc.test.e2e.error;

import com.github.henc.integrateboot.base.ResultInfo;
import com.github.henc.test.e2e.support.E2eTest;
import com.github.henc.test.e2e.support.ResultInfoAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@E2eTest
class ErrorApiE2eTest {

    @Autowired
    private RestTestClient client;

    @Test
    void businessExceptionReturnsFailureEnvelopeWithHttp200() {
        client.get().uri("/errors/business")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ResultInfo.class)
                .value(response -> ResultInfoAssert.failure(response, -1, "insufficient balance"));
    }

    @Test
    void notFoundExceptionReturnsFailureEnvelopeWithHttp404() {
        client.get().uri("/errors/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ResultInfo.class)
                .value(response -> ResultInfoAssert.failure(response, 404, "order 1 not found"));
    }

    @Test
    void validationFailureReturnsFieldDetailsInEnvelope() {
        client.post().uri("/errors/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"\",\"age\":200}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ResultInfo.class)
                .value(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getCode()).isEqualTo(400);
                    assertThat(response.getMessage()).contains("name:").contains("age:");
                });
    }
}
