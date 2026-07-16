package com.njydsz.pmis.common.socket.retry;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可重试的推送消息实体（P0-4）。
 *
 * <p>封装推送失败的消息及其重试状态，用于 {@link MessageRetryQueue} 管理。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryableMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一 ID（用于 ACK 去重） */
    private String messageId;

    /** 推送类型：USER / BROADCAST / TOPIC */
    private String pushType;

    /** 目标用户 ID（pushType=USER 时使用） */
    private String userId;

    /** 目标主题（pushType=TOPIC 时使用） */
    private String topic;

    /** 消息类型标签 */
    private String type;

    /** 消息内容 JSON */
    private String payloadJson;

    /** 链路追踪 ID */
    private String traceId;

    /** 当前重试次数 */
    private int retryCount;

    /** 最大重试次数 */
    private int maxRetries;

    /** 下次重试时间戳（毫秒） */
    private long nextRetryAt;

    /** 入队时间戳（毫秒） */
    private long enqueuedAt;

    /**
     * 构造用户推送重试消息。
     *
     * @param messageId  消息 ID
     * @param userId     用户 ID
     * @param type       消息类型
     * @param payloadJson 消息内容
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return 可重试消息
     */
    public static RetryableMessage forUser(String messageId, String userId, String type,
                                           String payloadJson, int maxRetries, long retryDelayMs) {
        return new RetryableMessage(messageId, "USER", userId, null, type, payloadJson,
                null, 0, maxRetries, System.currentTimeMillis() + retryDelayMs,
                System.currentTimeMillis());
    }

    /**
     * 构造广播重试消息。
     *
     * @param messageId  消息 ID
     * @param type       消息类型
     * @param payloadJson 消息内容
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return 可重试消息
     */
    public static RetryableMessage forBroadcast(String messageId, String type,
                                                 String payloadJson, int maxRetries, long retryDelayMs) {
        return new RetryableMessage(messageId, "BROADCAST", null, null, type, payloadJson,
                null, 0, maxRetries, System.currentTimeMillis() + retryDelayMs,
                System.currentTimeMillis());
    }

    /**
     * 构造主题推送重试消息。
     *
     * @param messageId  消息 ID
     * @param topic      主题
     * @param payloadJson 消息内容
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return 可重试消息
     */
    public static RetryableMessage forTopic(String messageId, String topic,
                                            String payloadJson, int maxRetries, long retryDelayMs) {
        return new RetryableMessage(messageId, "TOPIC", null, topic, null, payloadJson,
                null, 0, maxRetries, System.currentTimeMillis() + retryDelayMs,
                System.currentTimeMillis());
    }

    /**
     * 是否已超过最大重试次数。
     *
     * @return true 表示应移入死信队列
     */
    public boolean isMaxRetriesExceeded() {
        return retryCount >= maxRetries;
    }

    /**
     * 是否到期可重试。
     *
     * @return true 表示可以重试
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= nextRetryAt;
    }
}
