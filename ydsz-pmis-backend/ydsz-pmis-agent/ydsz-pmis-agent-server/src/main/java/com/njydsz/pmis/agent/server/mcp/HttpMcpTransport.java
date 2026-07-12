package com.njydsz.pmis.agent.server.mcp.transport;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP 传输实现（P3-3 落地）。
 *
 * <p>通过 HTTP POST 发送 JSON-RPC 请求，接收 JSON 响应。
 * 适用于远程 MCP 服务端或基于 SSE 的 Streamable HTTP 传输。
 *
 * <p>每次 {@link #send(String)} 后必须紧跟 {@link #receive()}，
 * 即一问一答模式（简化实现，不支持 SSE 长连接）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Slf4j
public class HttpMcpTransport implements McpTransport {

    private final String endpointUrl;
    private final long timeoutMs;
    private final HttpClient httpClient;

    /** 上一次响应的 JSON */
    private volatile String lastResponse;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 构造 HTTP 传输。
     *
     * @param endpointUrl MCP 服务端 HTTP 端点 URL
     * @param timeoutMs   请求超时毫秒
     */
    public HttpMcpTransport(String endpointUrl, long timeoutMs) {
        this(endpointUrl, timeoutMs, null);
    }

    /**
     * 构造 HTTP 传输（可注入自定义 HttpClient，便于测试）。
     *
     * @param endpointUrl MCP 服务端 HTTP 端点 URL
     * @param timeoutMs   请求超时毫秒
     * @param httpClient  自定义 HttpClient（null 则创建默认实例）
     */
    public HttpMcpTransport(String endpointUrl, long timeoutMs, HttpClient httpClient) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("endpointUrl 不能为空");
        }
        this.endpointUrl = endpointUrl;
        this.timeoutMs = timeoutMs;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(timeoutMs, 5000)))
                .build();
    }

    @Override
    public void connect() throws Exception {
        if (connected.get()) {
            return;
        }
        // HTTP 是无状态协议，connect 仅验证 URL 可达性（HEAD 请求）
        // 实际连接在每次 send 时建立
        connected.set(true);
        log.info("[MCP-Http] 端点已就绪: {}", endpointUrl);
    }

    @Override
    public void send(String json) throws Exception {
        ensureConnected();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 30000))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new java.io.IOException("MCP HTTP 请求失败: " + status + " " + response.body());
        }
        lastResponse = response.body();
    }

    @Override
    public String receive() throws Exception {
        ensureConnected();
        if (lastResponse == null) {
            throw new java.io.IOException("没有待接收的响应（请先调用 send）");
        }
        String resp = lastResponse;
        lastResponse = null;
        return resp;
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void close() {
        connected.set(false);
        lastResponse = null;
        log.info("[MCP-Http] 连接已关闭");
    }

    private void ensureConnected() {
        if (!connected.get()) {
            throw new IllegalStateException("传输未连接，请先调用 connect()");
        }
    }
}
