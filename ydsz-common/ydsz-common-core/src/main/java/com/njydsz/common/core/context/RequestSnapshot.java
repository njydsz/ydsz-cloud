package com.njydsz.common.core.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 请求的不可变快照。
 *
 * <p>用于在请求入口处一次性拷贝所需元数据，取代在 {@link RequestContext} 中直接持有
 * 活的 {@code HttpServletRequest} 对象（后者不可序列化、在异步 / 线程池边界易泄漏、
 * 且隐式绑死 Servlet API）。本类不依赖任何 Servlet 类型，可在任意线程、序列化边界安全传递。</p>
 *
 * <p>典型构造方式（在持有 {@code HttpServletRequest} 的 Filter / Interceptor 中）：</p>
 * <pre>{@code
 * RequestSnapshot snapshot = RequestSnapshot.of(
 *         request.getMethod(),
 *         request.getRequestURI(),
 *         request.getQueryString(),
 *         request.getRemoteAddr(),
 *         extractHeaders(request));   // 仅拷贝需要的 header
 * RequestContext.setRequestSnapshot(snapshot);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.9.1
 */
public final class RequestSnapshot {

    private final String method;
    private final String scheme;
    private final String requestUri;
    private final String queryString;
    private final String remoteAddr;
    private final String traceId;
    private final Map<String, String> headers;

    private RequestSnapshot(Builder b) {
        this.method = b.method;
        this.scheme = b.scheme;
        this.requestUri = b.requestUri;
        this.queryString = b.queryString;
        this.remoteAddr = b.remoteAddr;
        this.traceId = b.traceId;
        this.headers = b.headers != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(b.headers))
                : Collections.emptyMap();
    }

    public String getMethod() {
        return method;
    }

    public String getScheme() {
        return scheme;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public String getTraceId() {
        return traceId;
    }

    /** 返回不可变请求头视图（仅包含构造时拷贝的键）。 */
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * 构造不可变快照。
     *
     * @param method      请求方法（如 GET/POST），可为 null
     * @param requestUri  请求 URI（含 context path，不含 query），可为 null
     * @param queryString 查询字符串（不含前导 ?），可为 null
     * @param remoteAddr  客户端 IP，可为 null
     * @param headers     需要携带的请求头副本（会被拷贝为不可变 Map），可为 null
     * @return 不可变快照
     */
    public static RequestSnapshot of(String method, String requestUri, String queryString,
                                     String remoteAddr, Map<String, String> headers) {
        return builder()
                .method(method)
                .requestUri(requestUri)
                .queryString(queryString)
                .remoteAddr(remoteAddr)
                .headers(headers)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 快照构造器。 */
    public static final class Builder {
        private String method;
        private String scheme;
        private String requestUri;
        private String queryString;
        private String remoteAddr;
        private String traceId;
        private Map<String, String> headers;

        private Builder() {
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder requestUri(String requestUri) {
            this.requestUri = requestUri;
            return this;
        }

        public Builder queryString(String queryString) {
            this.queryString = queryString;
            return this;
        }

        public Builder remoteAddr(String remoteAddr) {
            this.remoteAddr = remoteAddr;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers != null ? new LinkedHashMap<>(headers) : null;
            return this;
        }

        public RequestSnapshot build() {
            return new RequestSnapshot(this);
        }
    }
}
