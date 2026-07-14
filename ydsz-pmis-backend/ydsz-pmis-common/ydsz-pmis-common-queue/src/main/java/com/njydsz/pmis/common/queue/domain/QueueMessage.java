package com.njydsz.pmis.common.queue.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.njydsz.pmis.common.util.id.TracerUtils;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.util.string.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一消息模型
 *
 * <p>封装消息队列中的消息内容，包含消息体、头部信息、追踪ID、重试次数等标准字段。
 * 支持序列化和反序列化，与 Redis Stream/List/PubSub 等多种队列实现无缝对接。
 *
 * <p><b>消息流程：</b>
 * <pre>{@code
 * // 创建消息
 * QueueMessage message = QueueMessage.of("hello world");
 *
 * // 发布消息
 * publisher.publish(message);
 *
 * // 消费消息
 * subscriber.subscribe(msg -> {
 *     log.info("Message: {}", msg.getBody());
 * });
 * }</pre>
 *
 * <p><b>字段说明：</b>
 * <ul>
 *   <li>body: 消息体，支持任意字符串内容</li>
 *   <li>headers: 消息头，用于传递元数据如消息类型、来源系统等</li>
 *   <li>traceId: 分布式追踪ID，用于日志关联和问题排查</li>
 *   <li>retryCount: 重试计数，记录消息被重试的次数</li>
 *   <li>timestamp: 消息创建时间戳</li>
 *   <li>priority: 消息优先级，数值越小优先级越高</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see TracerUtils
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 消息体内容
     */
    private String body;

    /**
     * 消息头信息
     * <p>用于传递元数据，如消息类型、来源系统、自定义属性等
     */
    private transient Map<String, String> headers;

    /**
     * 分布式追踪ID
     * <p>用于在分布式系统中关联日志链路，支持手动设置或自动生成
     */
    private String traceId;

    /**
     * 重试计数
     * <p>记录消息被消费失败后重试的次数，初始值为0
     */
    private Integer retryCount;

    /**
     * 消息创建时间戳
     * <p>格式：yyyy-MM-dd HH:mm:SSS
     */
    private String timestamp;

    /**
     * 消息优先级
     * <p>数值越小优先级越高，默认为 Integer.MAX_VALUE
     */
    private Integer priority;

    /**
     * 消息过期时间（单位：毫秒）
     * <p>可选字段，用于设置消息的 TTL，0 表示永不过期
     */
    private Long expireMillis;

    /**
     * 消息序号
     * <p>用于顺序消息场景，保证同一消息组内的消息按序号顺序处理
     */
    private Long sequenceNumber;

    /**
     * 消息分组键
     * <p>用于顺序消息场景，相同分组键的消息会被路由到同一队列分区，保证顺序性
     */
    private String messageGroupKey;

    /**
     * 创建简单文本消息
     *
     * @param body 消息体内容
     * @return 构建好的 QueueMessage 实例
     */
    public static QueueMessage of(String body) {
        QueueMessage message = new QueueMessage();
        message.setBody(body);
        message.setHeaders(new HashMap<>(4));
        message.setTraceId(generateTraceId());
        message.setRetryCount(0);
        message.setTimestamp(formatNow());
        message.setPriority(Integer.MAX_VALUE);
        message.setExpireMillis(0L);
        return message;
    }

    /**
     * 创建带优先级的消息
     *
     * @param body     消息体内容
     * @param priority 优先级（数值越小优先级越高）
     * @return 构建好的 QueueMessage 实例
     */
    public static QueueMessage of(String body, int priority) {
        QueueMessage message = of(body);
        message.setPriority(priority);
        return message;
    }

    /**
     * 创建带过期时间的消息
     *
     * @param body        消息体内容
     * @param expireTime  过期时间数值
     * @param timeUnit    时间单位
     * @return 构建好的 QueueMessage 实例
     */
    public static QueueMessage of(String body, long expireTime, TimeUnit timeUnit) {
        QueueMessage message = of(body);
        message.setExpireMillis(timeUnit.toMillis(expireTime));
        return message;
    }

    /**
     * 创建完整配置的消息
     *
     * @param body       消息体内容
     * @param headers    消息头信息
     * @param traceId    追踪ID
     * @param retryCount 重试次数
     * @return 构建好的 QueueMessage 实例
     */
    public static QueueMessage of(String body, Map<String, String> headers, String traceId, Integer retryCount) {
        QueueMessage message = new QueueMessage();
        message.setBody(body);
        message.setHeaders(headers == null ? new HashMap<>(4) : new HashMap<>(headers));
        message.setTraceId(StringUtils.isNotBlank(traceId) ? traceId : generateTraceId());
        message.setRetryCount(retryCount == null ? 0 : retryCount);
        message.setTimestamp(formatNow());
        message.setPriority(Integer.MAX_VALUE);
        message.setExpireMillis(0L);
        return message;
    }

    /**
     * 将消息对象序列化为 JSON 字符串
     *
     * <p>如果消息为 null，返回 null。如果某些字段为 null，会设置默认值：
     * <ul>
     *   <li>headers: 初始化为空 HashMap</li>
     *   <li>retryCount: 设置为 0</li>
     *   <li>traceId: 自动生成一个新的 UUID</li>
     *   <li>timestamp: 设置为当前时间</li>
     * </ul>
     *
     * @param message 消息对象
     * @return JSON 字符串，如果输入为 null 则返回 null
     */
    public static String toPayload(QueueMessage message) {
        if (message == null) {
            return null;
        }
        if (message.getHeaders() == null) {
            message.setHeaders(new HashMap<>(4));
        }
        if (message.getRetryCount() == null) {
            message.setRetryCount(0);
        }
        if (StringUtils.isBlank(message.getTraceId())) {
            message.setTraceId(generateTraceId());
        }
        if (StringUtils.isBlank(message.getTimestamp())) {
            message.setTimestamp(formatNow());
        }
        if (message.getPriority() == null) {
            message.setPriority(Integer.MAX_VALUE);
        }
        if (message.getExpireMillis() == null) {
            message.setExpireMillis(0L);
        }
        return Json.toJson(message);
    }

    /**
     * 将 JSON 字符串反序列化为消息对象
     *
     * <p>如果序列化失败或解析异常，会尝试将原始字符串作为消息体创建新消息。
     * 这确保了即使格式不标准的消息也能被处理。
     *
     * @param payload JSON 字符串
     * @return 消息对象，如果输入为空则返回 null
     */
    public static QueueMessage fromPayload(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            QueueMessage message = Json.toObject(payload, QueueMessage.class);
            if (message == null) {
                return QueueMessage.of(payload);
            }
            if (message.getHeaders() == null) {
                message.setHeaders(new HashMap<>(4));
            }
            if (message.getRetryCount() == null) {
                message.setRetryCount(0);
            }
            if (StringUtils.isBlank(message.getTraceId())) {
                message.setTraceId(generateTraceId());
            }
            if (StringUtils.isBlank(message.getTimestamp())) {
                message.setTimestamp(formatNow());
            }
            if (message.getPriority() == null) {
                message.setPriority(Integer.MAX_VALUE);
            }
            if (message.getExpireMillis() == null) {
                message.setExpireMillis(0L);
            }
            return message;
        } catch (Exception ex) {
            return QueueMessage.of(payload);
        }
    }

    /**
     * 添加消息头
     *
     * @param key   头信息的键
     * @param value 头信息的值
     * @return 当前消息实例，支持链式调用
     */
    public QueueMessage addHeader(String key, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>(4);
        }
        this.headers.put(key, value);
        return this;
    }

    /**
     * 获取消息头
     *
     * @param key 头信息的键
     * @return 头信息的值，如果不存在返回 null
     */
    public String getHeader(String key) {
        if (this.headers == null) {
            return null;
        }
        return this.headers.get(key);
    }

    /**
     * 检查消息是否已过期
     *
     * @return true 如果已过期，false 如果未过期或没有设置过期时间
     */
    public boolean isExpired() {
        if (this.expireMillis == null || this.expireMillis <= 0) {
            return false;
        }
        if (StringUtils.isBlank(this.timestamp)) {
            return false;
        }
        try {
            LocalDateTime createTime = LocalDateTime.parse(this.timestamp, TIMESTAMP_FORMATTER);
            LocalDateTime expireTime = createTime.plusNanos(this.expireMillis * 1_000_000);
            return LocalDateTime.now().isAfter(expireTime);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 检查消息是否为高优先级
     *
     * @param threshold 优先级阈值，默认 Integer.MAX_VALUE
     * @return true 如果优先级高于阈值
     */
    public boolean isHighPriority(int threshold) {
        return this.priority != null && this.priority < threshold;
    }

    /**
     * 检查消息是否为高优先级（使用默认阈值）
     *
     * @return true 如果优先级高于 1000
     */
    public boolean isHighPriority() {
        return isHighPriority(1000);
    }

    /**
     * 检查消息是否为顺序消息
     *
     * @return true 如果设置了消息分组键
     */
    public boolean isSequential() {
        return messageGroupKey != null && !messageGroupKey.isEmpty();
    }

    /**
     * 设置顺序消息属性
     *
     * @param messageGroupKey 消息分组键，相同分组的消息保证顺序
     * @param sequenceNumber  消息序号，用于组内排序
     * @return 当前消息实例，支持链式调用
     */
    public QueueMessage setSequential(String messageGroupKey, Long sequenceNumber) {
        this.messageGroupKey = messageGroupKey;
        this.sequenceNumber = sequenceNumber;
        return this;
    }

    /**
     * 增加重试次数
     *
     * @return 当前重试次数
     */
    public int incrementRetryCount() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        return ++this.retryCount;
    }

    /**
     * 重置消息状态
     * <p>清除重试计数，更新时间戳和追踪ID
     *
     * @return 当前消息实例，支持链式调用
     */
    public QueueMessage reset() {
        this.retryCount = 0;
        this.traceId = generateTraceId();
        this.timestamp = formatNow();
        return this;
    }

    /**
     * 获取消息摘要信息
     *
     * <p>用于日志记录和问题排查
     *
     * @return 格式化的摘要字符串
     */
    public String getSummary() {
        return String.format("[traceId=%s, retry=%d, priority=%d, bodyLen=%d]",
                this.traceId,
                this.retryCount,
                this.priority,
                this.body == null ? 0 : this.body.length());
    }

    /**
     * 生成新的追踪ID
     *
     * @return 格式化的追踪ID字符串
     */
    private static String generateTraceId() {
        return TracerUtils.generateTraceId();
    }

    /**
     * 格式化当前时间
     *
     * @return 格式化的时间字符串
     */
    private static String formatNow() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    @Override
    public String toString() {
        return "QueueMessage{" +
                "body='" + (body != null && body.length() > 100 ? body.substring(0, 100) + "..." : body) + '\'' +
                ", headers=" + (headers != null ? headers.size() + " entries" : "null") +
                ", traceId='" + traceId + '\'' +
                ", retryCount=" + retryCount +
                ", timestamp='" + timestamp + '\'' +
                ", priority=" + priority +
                ", expireMillis=" + expireMillis +
                '}';
    }
}