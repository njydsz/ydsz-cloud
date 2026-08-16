package com.njydsz.common.socket.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

/**
 * WebSocket 审计日志服务（P2-7 异步化重构）。
 *
 * <p>记录连接建立/断开、消息推送的结构化审计日志，
 * 通过专用 Logger {@code WS_AUDIT} 输出，便于日志采集和合规审计。
 *
 * <p><b>异步架构</b>：
 * 公共方法仅将审计事件放入有界队列（ring buffer），由后台单线程消费并写入日志。
 * 调用方无需等待 I/O，延迟稳定在微秒级。队列满时丢弃事件并计数（dropCount），
 * 避免审计日志影响核心业务性能。
 *
 * <p>审计字段：
 * <ul>
 *   <li>timestamp — 时间戳</li>
 *   <li>traceId — 链路追踪 ID</li>
 *   <li>event — 事件类型（CONNECT / DISCONNECT / PUSH）</li>
 *   <li>userId — 用户 ID（脱敏）</li>
 *   <li>sessionId — Session ID</li>
 *   <li>pushType — 推送类型（PUSH 事件）</li>
 *   <li>success — 是否成功</li>
 *   <li>durationMs — 耗时（毫秒）</li>
 *   <li>error — 错误信息（失败时）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WebSocketAuditService {

    /** 默认队列容量 */
    private static final int DEFAULT_QUEUE_CAPACITY = 4096;

    /** 用户 ID 脱敏保留长度 */
    private static final int MASK_KEEP_LENGTH = 3;

    /** 专用审计 Logger */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("WS_AUDIT");
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuditService.class);

    /** 有界队列（ring buffer） */
    private final BlockingQueue<AuditEvent> queue;

    /** 因队列满而丢弃的事件计数 */
    private final AtomicLong dropCount = new AtomicLong(0);

    /** 后台消费线程 */
    private Thread consumerThread;

    /** 运行标志 */
    private volatile boolean running = false;

    /**
     * 默认构造，使用 4096 容量的队列。
     */
    public WebSocketAuditService() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * 指定队列容量的构造。
     *
     * @param queueCapacity 队列容量（必须是 2 的幂）
     */
    public WebSocketAuditService(int queueCapacity) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    /**
     * 启动后台消费线程。
     */
    @PostConstruct
    public void start() {
        if (running) {
            return;
        }
        running = true;
        consumerThread = new Thread(this::consumeLoop, "ws-audit-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("[WS-Audit] 异步审计服务已启动, queueCapacity={}", queue.size() + queue.remainingCapacity());
    }

    /**
     * 优雅停机：停止消费并排空剩余事件。
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 排空剩余事件
        int drained = 0;
        while (!queue.isEmpty()) {
            AuditEvent event = queue.poll();
            if (event == null) {
                break;
            }
            writeAuditLog(event);
            drained++;
        }
        if (drained > 0) {
            log.info("[WS-Audit] 停机排空 {} 条审计事件", drained);
        }
    }

    /**
     * 审计连接建立事件。
     *
     * @param userId    用户 ID
     * @param sessionId Session ID
     * @param remoteIp  远程 IP
     */
    public void auditConnect(String userId, String sessionId, String remoteIp) {
        offerEvent(AuditEvent.connect(userId, sessionId, remoteIp));
    }

    /**
     * 审计连接断开事件。
     *
     * @param userId    用户 ID
     * @param sessionId Session ID
     * @param durationMs 连接时长（毫秒）
     */
    public void auditDisconnect(String userId, String sessionId, long durationMs) {
        offerEvent(AuditEvent.disconnect(userId, sessionId, durationMs));
    }

    /**
     * 审计消息推送事件。
     *
     * @param pushType  推送类型
     * @param userId    目标用户 ID（广播时为 null）
     * @param topic     目标主题（主题推送时使用）
     * @param success   是否成功
     * @param durationMs 耗时（毫秒）
     * @param error     错误信息（失败时）
     */
    public void auditPush(String pushType, String userId, String topic,
                          boolean success, long durationMs, String error) {
        offerEvent(AuditEvent.push(pushType, userId, topic, success, durationMs, error));
    }

    /**
     * 获取因队列满而丢弃的事件总数。
     *
     * @return 丢弃计数
     */
    public long getDropCount() {
        return dropCount.get();
    }

    /**
     * 获取当前队列中的待处理事件数。
     *
     * @return 队列深度
     */
    public int getPendingCount() {
        return queue.size();
    }

    // ======================== 内部实现 ========================

    /**
     * 将事件放入队列，队列满时丢弃并计数。
     */
    private void offerEvent(AuditEvent event) {
        if (!running) {
            // 队列未启动或已停止时直接写入（fallback to sync）
            writeAuditLog(event);
            return;
        }
        if (!queue.offer(event)) {
            dropCount.incrementAndGet();
            if (dropCount.get() == 1 || dropCount.get() % 1000 == 0) {
                log.warn("[WS-Audit] 审计队列已满, 累计丢弃 {} 条事件", dropCount.get());
            }
        }
    }

    /**
     * 后台消费循环。
     */
    private void consumeLoop() {
        while (running) {
            try {
                AuditEvent event = queue.poll();
                if (event != null) {
                    writeAuditLog(event);
                } else {
                    // 队列为空，短暂阻塞等待
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[WS-Audit] 消费异常: err={}", e.getMessage());
            }
        }
    }

    /**
     * 将审计事件序列化并写入日志。
     */
    private void writeAuditLog(AuditEvent event) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", event.timestamp);
        entry.put("traceId", event.traceId);
        entry.put("event", event.eventType);
        if (event.pushType != null) {
            entry.put("pushType", event.pushType);
        }
        if (event.userIdMask != null) {
            entry.put("userId_mask", event.userIdMask);
        }
        if (event.topic != null) {
            entry.put("topic", event.topic);
        }
        if (event.sessionId != null) {
            entry.put("sessionId", event.sessionId);
        }
        if (event.remoteIp != null) {
            entry.put("remoteIp", event.remoteIp);
        }
        if (event.durationMs != null) {
            entry.put("durationMs", event.durationMs);
        }
        if (event.success != null) {
            entry.put("success", event.success);
        }
        if (event.error != null) {
            entry.put("error", truncate(event.error, 500));
        }
        AUDIT_LOG.info(YdszJson.toJson(entry));
    }

    /**
     * 用户 ID 脱敏。
     */
    private static String maskUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "null";
        }
        if (userId.length() <= MASK_KEEP_LENGTH) {
            return "***";
        }
        return userId.substring(0, MASK_KEEP_LENGTH) + "***";
    }

    /**
     * 截断字符串。
     */
    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }

    // ======================== 不可变审计事件 ========================

    /**
     * 不可变审计事件对象。
     *
     * <p>在调用线程构造，捕获 traceId、timestamp 等上下文后入队列，
     * 消费线程仅读取，无并发竞争。
     */
    static class AuditEvent {
        final String timestamp;
        final String traceId;
        final String eventType;
        final String pushType;
        final String userIdMask;
        final String topic;
        final String sessionId;
        final String remoteIp;
        final Long durationMs;
        final Boolean success;
        final String error;

        AuditEvent(String timestamp, String traceId, String eventType,
                   String pushType, String userIdMask, String topic,
                   String sessionId, String remoteIp,
                   Long durationMs, Boolean success, String error) {
            this.timestamp = timestamp;
            this.traceId = traceId;
            this.eventType = eventType;
            this.pushType = pushType;
            this.userIdMask = userIdMask;
            this.topic = topic;
            this.sessionId = sessionId;
            this.remoteIp = remoteIp;
            this.durationMs = durationMs;
            this.success = success;
            this.error = error;
        }

        static AuditEvent connect(String userId, String sessionId, String remoteIp) {
            return new AuditEvent(
                    Instant.now().toString(),
                    WebSocketTraceContext.getTraceId(),
                    "CONNECT",
                    null, maskUserId(userId), null,
                    sessionId, remoteIp,
                    null, null, null);
        }

        static AuditEvent disconnect(String userId, String sessionId, long durationMs) {
            return new AuditEvent(
                    Instant.now().toString(),
                    WebSocketTraceContext.getTraceId(),
                    "DISCONNECT",
                    null, maskUserId(userId), null,
                    sessionId, null,
                    durationMs, null, null);
        }

        static AuditEvent push(String pushType, String userId, String topic,
                               boolean success, long durationMs, String error) {
            return new AuditEvent(
                    Instant.now().toString(),
                    WebSocketTraceContext.getTraceId(),
                    "PUSH",
                    pushType, userId != null ? maskUserId(userId) : null, topic,
                    null, null,
                    durationMs, success, error);
        }
    }
}
