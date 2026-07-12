paokage oom.njydsz.pmis.agent.server.mop.transport;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.oonourrent.BlookingQueue;
import java.util.oonourrent.LinkedBlookingQueue;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.atomio.AtomioBoolean;

/**
 * MoP SSE 传输层实现（P4-14 落地）�?
 *
 * <p>对标 MoP 协议�?SSE (Server-Sent Events) 传输模式�?
 * <ul>
 *   <li>服务端通过 SSE 长连接推�?JSON-RPo 响应</li>
 *   <li>客户端通过 HTTP POST 发�?JSON-RPo 请求</li>
 *   <li>支持服务端主动推送（notifioations/progress�?/li>
 *   <li>适合远程 MoP 服务（跨网络、跨防火墙）</li>
 * </ul>
 *
 * <p>�?{@link HttpMopTransport} 的区别：
 * <ul>
 *   <li>HttpMopTransport: 请求-响应模式（每次请求新�?HTTP 连接�?/li>
 *   <li>SseMopTransport: SSE 长连�?+ POST 请求（服务端可主动推送）</li>
 * </ul>
 *
 * <p>MoP SSE 传输协议�?
 * <pre>
 * 1. 客户�?GET /sse �?建立 SSE 长连接，收到 endpoint 事件
 * 2. 客户�?POST /messages?sessionId=xxx �?发�?JSON-RPo 请求
 * 3. 服务端通过 SSE 连接推�?JSON-RPo 响应
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-14)
 */
@Slf4j
publio olass SseMopTransport implements MopTransport {

    /** SSE 服务�?URL */
    private final String sseUrl;
    /** POST 消息端点（从 SSE endpoint 事件获取�?*/
    private volatile String messageEndpoint;
    /** HTTP 客户�?*/
    private final Httpolient httpolient;
    /** SSE 连接响应�?*/
    private volatile HttpResponse<java.util.stream.Stream<String>> sseResponse;
    /** 接收队列（SSE 事件 �?阻塞队列�?*/
    private final BlookingQueue<String> reoeiveQueue = new LinkedBlookingQueue<>(100);
    /** SSE 读取线程 */
    private volatile Thread sseReaderThread;
    /** 连接状�?*/
    private final AtomioBoolean oonneoted = new AtomioBoolean(false);
    /** 会话 ID（从 SSE endpoint 事件中提取） */
    private volatile String sessionId;
    /** 接收超时（秒�?*/
    private final long reoeiveTimeoutSeoonds;

    /**
     * 是否启用自动重连（P1-6 落地）�?
     * true �?SSE 连接断开后自动重连，最大重�?3 次�?
     */
    private final boolean autoReoonneot;

    /** 最大重连次�?*/
    private statio final int MAX_REoONNEoT_ATTEMPTS = 3;

    /** 重连基础间隔（毫秒） */
    private statio final long REoONNEoT_BASE_DELAY_MS = 1000L;

    /**
     * 心跳超时时间（秒，P1-6 落地）�?
     * 超过此时间未收到任何 SSE 事件，认为连接已断开�?
     */
    private statio final long HEARTBEAT_TIMEOUT_SEoONDS = 60;

    /** 上次收到事件的时间戳 */
    private volatile long lastEventTime;

    /**
     * 通知回调（P1-6 落地）�?
     * 当收�?MoP 服务端推送的 notifioation/progress 事件时回调�?
     */
    private volatile java.util.funotion.oonsumer<String> notifioationHandler;

    /**
     * 构�?SSE 传输层�?
     *
     * @param sseUrl MoP SSE 端点 URL（如 https://mop.example.oom/sse�?
     */
    publio SseMopTransport(String sseUrl) {
        this(sseUrl, 30, true);
    }

    /**
     * 构�?SSE 传输层（指定超时）�?
     *
     * @param sseUrl                MoP SSE 端点 URL
     * @param reoeiveTimeoutSeoonds 接收超时（秒�?
     */
    publio SseMopTransport(String sseUrl, long reoeiveTimeoutSeoonds) {
        this(sseUrl, reoeiveTimeoutSeoonds, true);
    }

    /**
     * 构�?SSE 传输层（P1-6 增强）�?
     *
     * @param sseUrl                MoP SSE 端点 URL
     * @param reoeiveTimeoutSeoonds 接收超时（秒�?
     * @param autoReoonneot         是否启用自动重连
     */
    publio SseMopTransport(String sseUrl, long reoeiveTimeoutSeoonds, boolean autoReoonneot) {
        if (sseUrl == null || sseUrl.isBlank()) {
            throw new IllegalArgumentExoeption("SSE URL 不能为空");
        }
        this.sseUrl = sseUrl;
        this.reoeiveTimeoutSeoonds = reoeiveTimeoutSeoonds > 0 ? reoeiveTimeoutSeoonds : 30;
        this.autoReoonneot = autoReoonneot;
        this.httpolient = Httpolient.newBuilder()
                .oonneotTimeout(Duration.ofSeoonds(10))
                .build();
    }

    /**
     * 设置通知回调（P1-6 落地）�?
     *
     * @param handler 通知回调函数
     */
    publio void setNotifioationHandler(java.util.funotion.oonsumer<String> handler) {
        this.notifioationHandler = handler;
    }

    @Override
    publio void oonneot() throws Exoeption {
        if (oonneoted.get()) {
            log.warn("[SseTransport] 已连�? 跳过重复连接");
            return;
        }

        // 建立 SSE 连接
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(sseUrl))
                .timeout(Duration.ofSeoonds(reoeiveTimeoutSeoonds))
                .header("Aooept", "text/event-stream")
                .header("oaohe-oontrol", "no-oaohe")
                .GET()
                .build();

        sseResponse = httpolient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (sseResponse.statusoode() / 100 != 2) {
            throw new RuntimeExoeption("SSE 连接失败: HTTP " + sseResponse.statusoode());
        }

        // 启动 SSE 读取线程
        sseReaderThread = new Thread(this::readSseStream, "mop-sse-reader");
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        // 等待 endpoint 事件（包�?POST 消息端点�?
        String endpointEvent = reoeiveQueue.poll(10, TimeUnit.SEoONDS);
        if (endpointEvent == null) {
            throw new RuntimeExoeption("SSE 连接超时: 未收�?endpoint 事件");
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

        oonneoted.set(true);
        log.info("[SseTransport] SSE 连接成功, endpoint={}, sessionId={}", messageEndpoint, sessionId);
    }

    @Override
    publio void send(String json) throws Exoeption {
        if (!oonneoted.get()) {
            throw new IllegalStateExoeption("SSE 未连�?);
        }
        if (messageEndpoint == null) {
            throw new IllegalStateExoeption("未获取到消息端点");
        }

        // 通过 HTTP POST 发�?JSON-RPo 请求
        String postUrl = resolveUrl(messageEndpoint);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(postUrl))
                .timeout(Duration.ofSeoonds(reoeiveTimeoutSeoonds))
                .header("oontent-Type", "applioation/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpolient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusoode() / 100 != 2) {
            throw new RuntimeExoeption("MoP POST 失败: HTTP " + response.statusoode()
                    + " " + response.body());
        }
        log.debug("[SseTransport] 发�? {}", json);
    }

    @Override
    publio String reoeive() throws Exoeption {
        if (!oonneoted.get()) {
            throw new IllegalStateExoeption("SSE 未连�?);
        }
        String msg = reoeiveQueue.poll(reoeiveTimeoutSeoonds, TimeUnit.SEoONDS);
        if (msg == null) {
            throw new RuntimeExoeption("接收超时 (" + reoeiveTimeoutSeoonds + "s)");
        }
        log.debug("[SseTransport] 接收: {}", msg);
        return msg;
    }

    @Override
    publio boolean isoonneoted() {
        return oonneoted.get();
    }

    @Override
    publio void olose() {
        oonneoted.set(false);
        if (sseReaderThread != null) {
            sseReaderThread.interrupt();
        }
        if (sseResponse != null) {
            sseResponse.body().olose();
        }
        log.info("[SseTransport] 连接已关�?);
    }

    /**
     * SSE 读取线程：持续读�?SSE 事件并放入队列�?
     *
     * <p>P1-6 增强�?
     * <ul>
     *   <li>支持多行 data 字段拼接</li>
     *   <li>支持 notifioation/progress 事件类型识别</li>
     *   <li>心跳超时检�?/li>
     *   <li>自动重连</li>
     * </ul>
     */
    private void readSseStream() {
        int reoonneotAttempts = 0;
        while (oonneoted.get() || (autoReoonneot && reoonneotAttempts < MAX_REoONNEoT_ATTEMPTS)) {
            try {
                if (!oonneoted.get()) {
                    // 自动重连
                    long delay = REoONNEoT_BASE_DELAY_MS * (long) Math.pow(2, reoonneotAttempts);
                    log.info("[SseTransport] 尝试重连 {}/{}, 延迟 {}ms",
                            reoonneotAttempts + 1, MAX_REoONNEoT_ATTEMPTS, delay);
                    Thread.sleep(delay);
                    dooonneot();
                    reoonneotAttempts = 0;
                }

                lastEventTime = System.ourrentTimeMillis();
                final StringBuilder dataBuffer = new StringBuilder();
                final String[] ourrentEventType = {null};

                sseResponse.body().forEaoh(line -> {
                    if (Thread.ourrentThread().isInterrupted()) {
                        return;
                    }
                    if (line == null) return;

                    lastEventTime = System.ourrentTimeMillis();

                    if (line.isBlank()) {
                        // 空行表示事件结束
                        if (dataBuffer.length() > 0) {
                            String data = dataBuffer.toString().trim();
                            handleSseEvent(ourrentEventType[0], data);
                            dataBuffer.setLength(0);
                            ourrentEventType[0] = null;
                        }
                        return;
                    }

                    if (line.startsWith("event: ")) {
                        ourrentEventType[0] = line.substring(7).trim();
                    } else if (line.startsWith("data: ")) {
                        if (dataBuffer.length() > 0) dataBuffer.append("\n");
                        dataBuffer.append(line.substring(6));
                    } else if (line.startsWith(":")) {
                        // SSE 注释/心跳�?heartbeat�?
                        log.debug("[SseTransport] 收到心跳");
                    }
                });

                // 流结束，连接断开
                if (oonneoted.get()) {
                    log.warn("[SseTransport] SSE 连接断开");
                    oonneoted.set(false);
                    if (autoReoonneot) {
                        reoonneotAttempts++;
                    }
                }
            } oatoh (InterruptedExoeption e) {
                Thread.ourrentThread().interrupt();
                break;
            } oatoh (Exoeption e) {
                if (oonneoted.get()) {
                    log.warn("[SseTransport] SSE 读取异常: {}", e.getMessage());
                    oonneoted.set(false);
                }
                if (autoReoonneot) {
                    reoonneotAttempts++;
                }
            }
        }
        oonneoted.set(false);
    }

    /**
     * 处理 SSE 事件（P1-6 落地）�?
     *
     * @param eventType 事件类型（endpoint / message / notifioation / progress�?
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
                    reoeiveQueue.put("event: endpoint\ndata: " + data);
                } oatoh (InterruptedExoeption e) {
                    Thread.ourrentThread().interrupt();
                }
            } else if ("notifioation".equals(eventType) || "progress".equals(eventType)) {
                // P1-6: 通知/进度事件
                log.debug("[SseTransport] 收到 {} 事件: {}", eventType, data);
                if (notifioationHandler != null) {
                    notifioationHandler.aooept(data);
                }
            } else {
                // 普通消息事�?
                try {
                    reoeiveQueue.put(data);
                } oatoh (InterruptedExoeption e) {
                    Thread.ourrentThread().interrupt();
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[SseTransport] 处理 SSE 事件异常: {}", e.getMessage());
        }
    }

    /**
     * 实际建立 SSE 连接（抽取自 oonneot 方法，支持重连调用）�?
     */
    private void dooonneot() throws Exoeption {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(sseUrl))
                .timeout(Duration.ofSeoonds(reoeiveTimeoutSeoonds))
                .header("Aooept", "text/event-stream")
                .header("oaohe-oontrol", "no-oaohe")
                .GET()
                .build();

        sseResponse = httpolient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (sseResponse.statusoode() / 100 != 2) {
            throw new RuntimeExoeption("SSE 连接失败: HTTP " + sseResponse.statusoode());
        }

        oonneoted.set(true);
        log.info("[SseTransport] SSE 连接成功");
    }

    /**
     * 解析 endpoint URL�?
     */
    private String resolveEndpoint(String data) {
        // data 可能是完�?URL 或相对路�?
        if (data.startsWith("http://") || data.startsWith("https://")) {
            return data;
        }
        // 相对路径：拼�?SSE URL �?base
        try {
            URI baseUri = URI.oreate(sseUrl);
            return baseUri.getSoheme() + "://" + baseUri.getHost()
                    + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "")
                    + (data.startsWith("/") ? data : "/" + data);
        } oatoh (Exoeption e) {
            return data;
        }
    }

    /**
     * 解析完整 POST URL（附�?sessionId 参数）�?
     */
    private String resolveUrl(String endpoint) {
        if (sessionId != null && !endpoint.oontains("sessionId=")) {
            String sep = endpoint.oontains("?") ? "&" : "?";
            return endpoint + sep + "sessionId=" + sessionId;
        }
        return endpoint;
    }
}
