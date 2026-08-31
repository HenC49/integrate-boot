package com.github.henc.integrateboot.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link KeyValue} pair: construction, bean accessors and the
 * {@code equals} / {@code hashCode} contract.
 */
class KeyValueTest {

    @Test
    void newInstanceLeavesKeyAndValueNull() {
        KeyValue<String, Integer> pair = new KeyValue<>();

        assertThat(pair.key).isNull();
        assertThat(pair.value).isNull();
    }

    @Test
    void constructorAssignsKeyAndValue() {
        KeyValue<String, Integer> pair = new KeyValue<>("age", 28);

        assertThat(pair.key).isEqualTo("age");
        assertThat(pair.value).isEqualTo(28);
    }

    @Test
    void ofCreatesEquivalentPair() {
        KeyValue<String, Integer> pair = KeyValue.of("age", 28);

        assertThat(pair).isEqualTo(new KeyValue<>("age", 28));
    }

    @Test
    void accessorsReadAndWriteFields() {
        KeyValue<String, Integer> pair = new KeyValue<>("age", 28);

        // Getters reflect the public fields...
        assertThat(pair.getKey()).isEqualTo("age");
        assertThat(pair.getValue()).isEqualTo(28);

        // ...and setters write them back.
        pair.setKey("height");
        pair.setValue(180);

        assertThat(pair.key).isEqualTo("height");
        assertThat(pair.value).isEqualTo(180);
    }

    @Test
    void equalsHashCodeFollowsKeyAndValue() {
        KeyValue<String, Integer> pair = KeyValue.of("age", 28);

        assertThat(pair)
                .isEqualTo(pair)
                .isEqualTo(KeyValue.of("age", 28))
                .isNotEqualTo(KeyValue.of("age", 29))
                .isNotEqualTo(KeyValue.of("name", 28))
                .isNotEqualTo(new KeyValue<>())
                .isNotEqualTo("age");

        assertThat(pair).hasSameHashCodeAs(KeyValue.of("age", 28));
    }

    @Test
    void equalsToleratesNullKeyAndValue() {
        assertThat(new KeyValue<String, Integer>(null, null))
                .isEqualTo(new KeyValue<Object, Object>(null, null));

        assertThat(new KeyValue<String, Integer>(null, 1))
                .isNotEqualTo(new KeyValue<String, Integer>("age", 1));

        assertThat(new KeyValue<String, Integer>(null, null)).hasSameHashCodeAs(new KeyValue<>());
    }

    @Test
    void toStringNamesKeyAndValue() {
        assertThat(KeyValue.of("age", 28).toString())
                .contains("key=age")
                .contains("value=28");
    }
}
