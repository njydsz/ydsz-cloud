package com.njydsz.pmis.message.constant;

/**
 * 消息通知引擎常量集中定义。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class MessageConstants {

    private MessageConstants() {
    }

    /** 默认租户 ID */
    public static final String DEFAULT_TENANT_ID = "1";

    /** 默认语言 */
    public static final String DEFAULT_LOCALE = "zh-CN";

    /** 默认业务类型（偏好表占位） */
    public static final String DEFAULT_BIZ_TYPE = "__DEFAULT__";

    /** 消费幂等 key 前缀 */
    public static final String IDEMPOTENT_KEY_PREFIX = "pmis:msg:idempotent:";

    /** 消费幂等 TTL（秒），覆盖 RocketMQ 重投窗口 */
    public static final long IDEMPOTENT_TTL_SECONDS = 600L;

    /** 限流 key 前缀 */
    public static final String RATE_LIMIT_KEY_PREFIX = "pmis:msg:ratelimit:";

    /** 聚合批次锁前缀 */
    public static final String AGGREGATE_LOCK_PREFIX = "pmis:msg:aggregate:lock:";

    /** 路由规则缓存 key */
    public static final String ROUTE_RULE_CACHE_KEY = "pmis:msg:route:rules";

    /** 灰度桶缓存 key 前缀 */
    public static final String CANARY_CACHE_PREFIX = "pmis:msg:canary:";

    /** 频率统计 key 前缀（每日 / 每小时） */
    public static final String FREQUENCY_DAILY_PREFIX = "pmis:msg:freq:daily:";
    public static final String FREQUENCY_HOURLY_PREFIX = "pmis:msg:freq:hourly:";

    /** WebSocket 用户订阅前缀 */
    public static final String WS_USER_DESTINATION_PREFIX = "/topic/user/";
    public static final String WS_BROADCAST_DESTINATION = "/topic/broadcast";
    public static final String WS_TOPIC_DESTINATION_PREFIX = "/topic/";

    /** 消息发送超时（毫秒） */
    public static final long SEND_TIMEOUT_MS = 5000L;

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;
}
