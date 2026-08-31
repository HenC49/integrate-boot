package com.github.henc.integrateboot.base;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base response entity shared by every integrate-boot service: a stable envelope of
 * {@code success} / {@code code} / {@code message} plus an open {@code result} map for
 * payload entries.
 *
 * <p>The fields are {@code protected} so a service can extend this class with its own
 * strongly-typed result shape while keeping the wire contract intact. The static
 * {@link #success()} / {@link #failure(String)} factories cover the common cases:
 *
 * <pre>{@code
 * // {"success":true,"code":0,"result":{"user":...}}
 * return ResultInfo.success().put("user", user).put("roles", roles);
 *
 * // {"success":false,"code":-1,"message":"user not found"}
 * return ResultInfo.failure("user not found");
 * }</pre>
 */
public class ResultInfo implements Serializable {

    /**
     * Code of a successful response (the default {@link #code}).
     */
    public static final int CODE_SUCCESS = 0;

    /**
     * Code used by {@link #failure(String)} when no explicit code is given. Any non-zero
     * code means failure; services are free to define their own error-code ranges.
     */
    public static final int CODE_FAILURE = -1;

    private static final long serialVersionUID = 1L;

    protected boolean success = false;
    protected Integer code = 0;
    protected String message = null;
    protected String requestId = null;
    protected Map<String, Object> result = new HashMap<>();

    /**
     * Creates a success envelope: {@code success=true}, {@code code=0}, empty result.
     *
     * @return a fresh, mutable {@code ResultInfo}
     */
    public static ResultInfo success() {
        ResultInfo info = new ResultInfo();
        info.success = true;
        info.code = CODE_SUCCESS;
        return info;
    }

    /**
     * Creates a success envelope carrying one payload entry under {@code key}.
     *
     * @param key   result-map key
     * @param value result-map value
     * @return a fresh, mutable {@code ResultInfo}
     */
    public static ResultInfo success(String key, Object value) {
        return success().put(key, value);
    }

    /**
     * Creates a success envelope carrying the given payload entries.
     *
     * @param result entries copied into the result map; {@code null} yields an empty map
     * @return a fresh, mutable {@code ResultInfo}
     */
    public static ResultInfo success(Map<String, Object> result) {
        return success().putAll(result);
    }

    /**
     * Creates a failure envelope with the default failure code ({@value #CODE_FAILURE}).
     *
     * @param message human-readable failure reason
     * @return a fresh, mutable {@code ResultInfo}
     */
    public static ResultInfo failure(String message) {
        return failure(CODE_FAILURE, message);
    }

    /**
     * Creates a failure envelope with an explicit error code.
     *
     * @param code    service-defined error code (any non-zero value)
     * @param message human-readable failure reason
     * @return a fresh, mutable {@code ResultInfo}
     */
    public static ResultInfo failure(int code, String message) {
        ResultInfo info = new ResultInfo();
        info.success = false;
        info.code = code;
        info.message = message;
        return info;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = Objects.requireNonNullElseGet(result, HashMap::new);
    }

    /**
     * Puts one entry into the result map.
     *
     * @param key   result-map key
     * @param value result-map value
     * @return this, for chaining
     */
    public ResultInfo put(String key, Object value) {
        result.put(key, value);
        return this;
    }

    /**
     * Copies all entries of {@code values} into the result map.
     *
     * @param values entries to copy; {@code null} is a no-op
     * @return this, for chaining
     */
    public ResultInfo putAll(Map<String, Object> values) {
        if (values != null) {
            result.putAll(values);
        }
        return this;
    }

    @Override
    public String toString() {
        return "ResultInfo{success=" + success
                + ", code=" + code
                + ", message=" + message
                + ", requestId=" + requestId
                + ", result=" + result
                + '}';
    }
}
