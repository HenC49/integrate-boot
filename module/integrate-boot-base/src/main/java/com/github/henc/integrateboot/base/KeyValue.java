package com.github.henc.integrateboot.base;

import java.io.Serializable;
import java.util.Objects;

/**
 * A minimal generic key-value pair — the shared currency for ordered pairs of a key and a
 * value where a {@code Map} entry would not fit (e.g. list elements, DTO fields, tuples
 * returned to front ends).
 *
 * <p>Both fields are public and mutable, so instances double as beans: the getters/setters
 * exist for frameworks that bind through properties.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class KeyValue<K, V> implements Serializable {

    private static final long serialVersionUID = 1L;

    public K key;

    public V value;

    /**
     * Creates an empty pair ({@code key=null}, {@code value=null}).
     */
    public KeyValue() {
    }

    /**
     * Creates a pair of the given key and value.
     *
     * @param key   the key
     * @param value the value
     */
    public KeyValue(K key, V value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Creates a pair of the given key and value.
     *
     * @param key   the key
     * @param value the value
     * @param <K>   key type
     * @param <V>   value type
     * @return a new {@code KeyValue}
     */
    public static <K, V> KeyValue<K, V> of(K key, V value) {
        return new KeyValue<>(key, value);
    }

    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KeyValue<?, ?> other)) {
            return false;
        }
        return Objects.equals(key, other.key) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "KeyValue{key=" + key + ", value=" + value + '}';
    }
}
