package com.github.henc.integrateboot.base;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ResultInfo} envelope: defaults, static factories and the
 * fluent result-map helpers.
 */
class ResultInfoTest {

    @Test
    void newInstanceHasSafeDefaults() {
        ResultInfo info = new ResultInfo();

        assertThat(info.isSuccess()).isFalse();
        assertThat(info.getCode()).isZero();
        assertThat(info.getMessage()).isNull();
        assertThat(info.getRequestId()).isNull();
        // The result map is always usable, never null.
        assertThat(info.getResult()).isNotNull().isEmpty();
    }

    @Test
    void successFactoryMarksSuccessWithZeroCode() {
        ResultInfo info = ResultInfo.success();

        assertThat(info.isSuccess()).isTrue();
        assertThat(info.getCode()).isEqualTo(ResultInfo.CODE_SUCCESS);
        assertThat(info.getResult()).isEmpty();
    }

    @Test
    void successWithKeyValueCarriesEntry() {
        ResultInfo info = ResultInfo.success("user", "alice");

        assertThat(info.isSuccess()).isTrue();
        assertThat(info.getResult()).containsEntry("user", "alice");
    }

    @Test
    void successWithMapCopiesEntriesAndToleratesNull() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", 1);
        payload.put("name", "alice");

        ResultInfo info = ResultInfo.success(payload);

        assertThat(info.getResult()).containsAllEntriesOf(payload);

        // A null map degrades to an empty result instead of failing.
        assertThat(ResultInfo.success(null).getResult()).isEmpty();
    }

    @Test
    void successWithMapIsCopyNotSharedReference() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", 1);

        ResultInfo info = ResultInfo.success(payload);
        payload.put("id", 2);

        // Entries are copied in — later changes to the source map must not leak in.
        assertThat(info.getResult()).containsEntry("id", 1);
    }

    @Test
    void failureDefaultsToFailureCode() {
        ResultInfo info = ResultInfo.failure("user not found");

        assertThat(info.isSuccess()).isFalse();
        assertThat(info.getCode()).isEqualTo(ResultInfo.CODE_FAILURE);
        assertThat(info.getMessage()).isEqualTo("user not found");
    }

    @Test
    void failureAcceptsExplicitCode() {
        ResultInfo info = ResultInfo.failure(40401, "user not found");

        assertThat(info.isSuccess()).isFalse();
        assertThat(info.getCode()).isEqualTo(40401);
        assertThat(info.getMessage()).isEqualTo("user not found");
    }

    @Test
    void putChainsAndAccumulatesEntries() {
        ResultInfo info = ResultInfo.success();

        ResultInfo returned = info.put("user", "alice").put("roles", "admin");

        // Fluent: the same instance comes back for chaining.
        assertThat(returned).isSameAs(info);
        assertThat(info.getResult())
                .containsEntry("user", "alice")
                .containsEntry("roles", "admin");
    }

    @Test
    void putAllCopiesEntriesAndToleratesNull() {
        Map<String, Object> payload = Map.of("count", 3);

        ResultInfo info = ResultInfo.success().putAll(payload).putAll(null);

        assertThat(info.getResult()).containsEntry("count", 3);
    }

    @Test
    void setResultReplacesMapAndNormalizesNullToEmpty() {
        ResultInfo info = new ResultInfo();

        info.setResult(Map.of("id", 7));
        assertThat(info.getResult()).containsEntry("id", 7);

        info.setResult(null);
        assertThat(info.getResult()).isNotNull().isEmpty();
    }

    @Test
    void beanAccessorsRoundTrip() {
        ResultInfo info = new ResultInfo();
        info.setSuccess(true);
        info.setCode(100);
        info.setMessage("ok");
        info.setRequestId("req-42");

        assertThat(info.isSuccess()).isTrue();
        assertThat(info.getCode()).isEqualTo(100);
        assertThat(info.getMessage()).isEqualTo("ok");
        assertThat(info.getRequestId()).isEqualTo("req-42");
    }

    @Test
    void toStringNamesAllFields() {
        ResultInfo info = ResultInfo.failure("boom");
        info.setRequestId("req-7");

        assertThat(info.toString())
                .contains("success=false")
                .contains("code=-1")
                .contains("message=boom")
                .contains("requestId=req-7")
                .contains("result=");
    }
}
