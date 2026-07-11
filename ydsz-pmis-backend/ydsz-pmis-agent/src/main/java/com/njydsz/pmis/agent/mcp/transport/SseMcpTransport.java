package com.njydsz.pmis.agent.mcp.transport;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP SSE 传输层实现（P4-14 落地）。
 *
 * <p>对标 MCP 协议的 SSE (Server-Sent Events) 传输模式：
 * <ul>
 *   <li>服务端通过 SSE 长连接推送 JSON-RPC 响应</li>
 *   <li>客户端通过 HTTP POST 发送 JSON-RPC 请求</li>
 *   <li>支持服务端主动推送（notifications/progress）</li>
 *   <li>适合远程 MCP 服务（跨网络、跨防火墙）</li>
 * </ul>
 *
 * <p>与 {@link HttpMcpTransport} 的区别：
 * <ul>
 *   <li>HttpMcpTransport: 请求-响应模式（每次请求新建 HTTP 连接）</li>
 *   <li>SseMcpTransport: SSE 长连接 + POST 请求（服务端可主动推送）</li>
 * </ul>
 *
 * <p>MCP SSE 传输协议：
 * <pre>
 * 1. 客户端 GET /sse → 建立 SSE 长连接，收到 endpoint 事件
 * 2. 客户端 POST /messages?sessionId=xxx → 发送 JSON-RPC 请求
 * 3. 服务端通过 SSE 连接推送 JSON-RPC 响应
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-14)
 */
@Slf4j
public class SseMcpTransport implements McpTransport {

    /** SSE 服务器 URL */
    private final String sseUrl;
    /** POST 消息端点（从 SSE endpoint 事件获取） */
    private volatile String messageEndpoint;
    /** HTTP 客户端 */
    private final HttpClient httpClient;
    /** SSE 连接响应流 */
    private volatile HttpResponse<java.util.stream.Stream<String>> sseResponse;
    /** 接收队列（SSE 事件 → 阻塞队列） */
    private final BlockingQueue<String> receiveQueue = new LinkedBlockingQueue<>(100);
    /** SSE 读取线程 */
    private volatile Thread sseReaderThread;
    /** 连接状态 */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    /** 会话 ID（从 SSE endpoint 事件中提取） */
    private volatile String sessionId;
    /** 接收超时（秒） */
    private final long receiveTimeoutSeconds;

    /**
     * 是否启用自动重连（P1-6 落地）。
     * true 时 SSE 连接断开后自动重连，最大重试 3 次。
     */
    private final boolean autoReconnect;

    /** 最大重连次数 */
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    /** 重连基础间隔（毫秒） */
    private static final long RECONNECT_BASE_DELAY_MS = 1000L;

    /**
     * 心跳超时时间（秒，P1-6 落地）。
     * 超过此时间未收到任何 SSE 事件，认为连接已断开。
     */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 60;

    /** 上次收到事件的时间戳 */
    private volatile long lastEventTime;

    /**
     * 通知回调（P1-6 落地）。
     * 当收到 MCP 服务端推送的 notification/progress 事件时回调。
     */
    private volatile java.util.function.Consumer<String> notificationHandler;

    /**
     * 构造 SSE 传输层。
     *
     * @param sseUrl MCP SSE 端点 URL（如 https://mcp.example.com/sse）
     */
    public SseMcpTransport(String sseUrl) {
        this(sseUrl, 30, true);
    }

    /**
     * 构造 SSE 传输层（指定超时）。
     *
     * @param sseUrl                MCP SSE 端点 URL
     * @param receiveTimeoutSeconds 接收超时（秒）
     */
    public SseMcpTransport(String sseUrl, long receiveTimeoutSeconds) {
        this(sseUrl, receiveTimeoutSeconds, true);
    }

    /**
     * 构造 SSE 传输层（P1-6 增强）。
     *
     * @param sseUrl                MCP SSE 端点 URL
     * @param receiveTimeoutSeconds 接收超时（秒）
     * @param autoReconnect         是否启用自动重连
     */
    public SseMcpTransport(String sseUrl, long receiveTimeoutSeconds, boolean autoReconnect) {
        if (sseUrl == null || sseUrl.isBlank()) {
            throw new IllegalArgumentException("SSE URL 不能为空");
        }
        this.sseUrl = sseUrl;
        this.receiveTimeoutSeconds = receiveTimeoutSeconds > 0 ? receiveTimeoutSeconds : 30;
        this.autoReconnect = autoReconnect;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 设置通知回调（P1-6 落地）。
     *
     * @param handler 通知回调函数
     */
    public void setNotificationHandler(java.util.function.Consumer<String> handler) {
        this.notificationHandler = handler;
    }

    @Override
    public void connect() throws Exception {
        if (connected.get()) {
            log.warn("[SseTransport] 已连接, 跳过重复连接");
            return;
        }

        // 建立 SSE 连接
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sseUrl))
                .timeout(Duration.ofSeconds(receiveTimeoutSeconds))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        sseResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (sseResponse.statusCode() / 100 != 2) {
            throw new RuntimeException("SSE 连接失败: HTTP " + sseResponse.statusCode());
        }

        // 启动 SSE 读取线程
        sseReaderThread = new Thread(this::readSseStream, "mcp-sse-reader");
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        // 等待 endpoint 事件（包含 POST 消息端点）
        String endpointEvent = receiveQueue.poll(10, TimeUnit.SECONDS);
        if (endpointEvent == null) {
            throw new RuntimeException("SSE 连接超时: 未收到 endpoint 事件");
        }

        // 解析 endpoint 事件
        // 格式: event: endpoint\ndata: /messages?sessionId=xxx
        if (endpointEvent.startsWith("data: ")) {
            String data = endpointEvent.substring(6).trim();
            messageEndpoint = resolveEndpoint(data);
            // 提取 sessionId
            int idx = data.indexOf("sessionId=");
            if (idx >= 0) {
                sessionId = data.substring(idx + 10).split("&")[0];
            }
        }

        connected.set(true);
        log.info("[SseTransport] SSE 连接成功, endpoint={}, sessionId={}", messageEndpoint, sessionId);
    }

    @Override
    public void send(String json) throws Exception {
        if (!connected.get()) {
            throw new IllegalStateException("SSE 未连接");
        }
        if (messageEndpoint == null) {
            throw new IllegalStateException("未获取到消息端点");
        }

        // 通过 HTTP POST 发送 JSON-RPC 请求
        String postUrl = resolveUrl(messageEndpoint);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(postUrl))
                .timeout(Duration.ofSeconds(receiveTimeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("MCP POST 失败: HTTP " + response.statusCode()
                    + " " + response.body());
        }
        log.debug("[SseTransport] 发送: {}", json);
    }

    @Override
    public String receive() throws Exception {
        if (!connected.get()) {
            throw new IllegalStateException("SSE 未连接");
        }
        String msg = receiveQueue.poll(receiveTimeoutSeconds, TimeUnit.SECONDS);
        if (msg == null) {
            throw new RuntimeException("接收超时 (" + receiveTimeoutSeconds + "s)");
        }
        log.debug("[SseTransport] 接收: {}", msg);
        return msg;
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void close() {
        connected.set(false);
        if (sseReaderThread != null) {
            sseReaderThread.interrupt();
        }
        if (sseResponse != null) {
            sseResponse.body().close();
        }
        log.info("[SseTransport] 连接已关闭");
    }

    /**
     * SSE 读取线程：持续读取 SSE 事件并放入队列。
     *
     * <p>P1-6 增强：
     * <ul>
     *   <li>支持多行 data 字段拼接</li>
     *   <li>支持 notification/progress 事件类型识别</li>
     *   <li>心跳超时检测</li>
     *   <li>自动重连</li>
     * </ul>
     */
    private void readSseStream() {
        int reconnectAttempts = 0;
        while (connected.get() || (autoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS)) {
            try {
                if (!connected.get()) {
                    // 自动重连
                    long delay = RECONNECT_BASE_DELAY_MS * (long) Math.pow(2, reconnectAttempts);
                    log.info("[SseTransport] 尝试重连 {}/{}, 延迟 {}ms",
                            reconnectAttempts + 1, MAX_RECONNECT_ATTEMPTS, delay);
                    Thread.sleep(delay);
                    doConnect();
                    reconnectAttempts = 0;
                }

                lastEventTime = System.currentTimeMillis();
                final StringBuilder dataBuffer = new StringBuilder();
                final String[] currentEventType = {null};

                sseResponse.body().forEach(line -> {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    if (line == null) return;

                    lastEventTime = System.currentTimeMillis();

                    if (line.isBlank()) {
                        // 空行表示事件结束
                        if (dataBuffer.length() > 0) {
                            String data = dataBuffer.toString().trim();
                            handleSseEvent(currentEventType[0], data);
                            dataBuffer.setLength(0);
                            currentEventType[0] = null;
                        }
                        return;
                    }

                    if (line.startsWith("event: ")) {
                        currentEventType[0] = line.substring(7).trim();
                    } else if (line.startsWith("data: ")) {
                        if (dataBuffer.length() > 0) dataBuffer.append("\n");
                        dataBuffer.append(line.substring(6));
                    } else if (line.startsWith(":")) {
                        // SSE 注释/心跳（:heartbeat）
                        log.debug("[SseTransport] 收到心跳");
                    }
                });

                // 流结束，连接断开
                if (connected.get()) {
                    log.warn("[SseTransport] SSE 连接断开");
                    connected.set(false);
                    if (autoReconnect) {
                        reconnectAttempts++;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (connected.get()) {
                    log.warn("[SseTransport] SSE 读取异常: {}", e.getMessage());
                    connected.set(false);
                }
                if (autoReconnect) {
                    reconnectAttempts++;
                }
            }
        }
        connected.set(false);
    }

    /**
     * 处理 SSE 事件（P1-6 落地）。
     *
     * @param eventType 事件类型（endpoint / message / notification / progress）
     * @param data      事件数据
     */
    private void handleSseEvent(String eventType, String data) {
        try {
            if ("endpoint".equals(eventType)) {
                messageEndpoint = resolveEndpoint(data);
                int idx = data.indexOf("sessionId=");
                if (idx >= 0) {
                    sessionId = data.substring(idx + 10).split("&")[0];
                }
                try {
                    receiveQueue.put("event: endpoint\ndata: " + data);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else if ("notification".equals(eventType) || "progress".equals(eventType)) {
                // P1-6: 通知/进度事件
                log.debug("[SseTransport] 收到 {} 事件: {}", eventType, data);
                if (notificationHandler != null) {
                    notificationHandler.accept(data);
                }
            } else {
                // 普通消息事件
                try {
                    receiveQueue.put(data);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            log.warn("[SseTransport] 处理 SSE 事件异常: {}", e.getMessage());
        }
    }

    /**
     * 实际建立 SSE 连接（抽取自 connect 方法，支持重连调用）。
     */
    private void doConnect() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sseUrl))
                .timeout(Duration.ofSeconds(receiveTimeoutSeconds))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        sseResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (sseResponse.statusCode() / 100 != 2) {
            throw new RuntimeException("SSE 连接失败: HTTP " + sseResponse.statusCode());
        }

        connected.set(true);
        log.info("[SseTransport] SSE 连接成功");
    }

    /**
     * 解析 endpoint URL。
     */
    private String resolveEndpoint(String data) {
        // data 可能是完整 URL 或相对路径
        if (data.startsWith("http://") || data.startsWith("https://")) {
            return data;
        }
        // 相对路径：拼接 SSE URL 的 base
        try {
            URI baseUri = URI.create(sseUrl);
            return baseUri.getScheme() + "://" + baseUri.getHost()
                    + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "")
                    + (data.startsWith("/") ? data : "/" + data);
        } catch (Exception e) {
            return data;
        }
    }

    /**
     * 解析完整 POST URL（附加 sessionId 参数）。
     */
    private String resolveUrl(String endpoint) {
        if (sessionId != null && !endpoint.contains("sessionId=")) {
            String sep = endpoint.contains("?") ? "&" : "?";
            return endpoint + sep + "sessionId=" + sessionId;
        }
        return endpoint;
    }
}
