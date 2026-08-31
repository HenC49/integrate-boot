package com.github.henc.test.e2e.support;

import com.github.henc.integrateboot.base.ResultInfo;

import static org.assertj.core.api.Assertions.assertThat;

/** Assertions for the shared failure response envelope. */
public final class ResultInfoAssert {

    private ResultInfoAssert() {
    }

    public static void failure(ResultInfo response, int code, String message) {
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(code);
        assertThat(response.getMessage()).isEqualTo(message);
    }
}
