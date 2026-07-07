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

    /** P2-5: 多维度限流 key 前缀 */
    public static final String RATE_LIMIT_RECEIVER_PREFIX = "pmis:msg:ratelimit:receiver:";
    public static final String RATE_LIMIT_TEMPLATE_PREFIX = "pmis:msg:ratelimit:template:";
    public static final String RATE_LIMIT_TENANT_PREFIX = "pmis:msg:ratelimit:tenant:";

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

    /** 重试基础退避（毫秒），实际退避 = base * 2^retryCount */
    public static final long RETRY_BASE_BACKOFF_MS = 2000L;

    /** 重试扫描分布式锁 key */
    public static final String RETRY_SCAN_LOCK_KEY = "pmis:msg:retry:scan:lock";

    /** 聚合批次扫描分布式锁 key (P2-4: 多实例部署保证唯一执行) */
    public static final String AGGREGATE_SCAN_LOCK_KEY = "pmis:msg:aggregate:scan:lock";

    /** 单次重试扫描批量大小 */
    public static final int RETRY_SCAN_BATCH_SIZE = 200;

    /** 单次批量发送最大条数(防止阻塞过久) */
    public static final int BATCH_SEND_MAX_SIZE = 100;
}
