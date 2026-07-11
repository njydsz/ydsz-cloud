package com.njydsz.pmis.message.domain.constant;


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

    /** P2-1: 智能去重 key 前缀（SET NX EX 原子去重） */
    public static final String DEDUP_KEY_PREFIX = "pmis:msg:dedup:";

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

    /** P2-6: 级联发送最大深度(防止无限递归,顶层消息深度=0) */
    public static final int MAX_CASCADE_DEPTH = 5;

    /** P2-9: 回执拉取调度器分布式锁 key */
    public static final String RECEIPT_PULL_LOCK_KEY = "pmis:msg:receipt:pull:lock";

    /** P2-9: 单次回执拉取扫描批量大小 */
    public static final int RECEIPT_PULL_BATCH_SIZE = 200;

    /** P2-9: 回执拉取延迟阈值(分钟): 发送成功后多少分钟才开始主动拉取回执(给服务商回调留窗口) */
    public static final long RECEIPT_PULL_DELAY_MINUTES = 5L;

    /** P2-9: 回执超时阈值(分钟): 超过此时间仍未收到回执则标记为 TIMEOUT */
    public static final long RECEIPT_TIMEOUT_MINUTES = 30L;

    // ========== P0-4: WebSocket 鉴权 / 在线状态 / 离线消息补偿 ==========

    /** P0-4: 在线用户 Redis key 前缀（Hash: pmis:ws:online:{userId} -> sessionId） */
    public static final String WS_ONLINE_KEY_PREFIX = "pmis:ws:online:";

    /** P0-4: 离线消息 Redis List key 前缀（pmis:ws:offline:{userId}） */
    public static final String WS_OFFLINE_KEY_PREFIX = "pmis:ws:offline:";

    /** P0-4: 离线消息缓存最大条数（防止内存溢出，FIFO 淘汰） */
    public static final int WS_OFFLINE_MAX_CACHE = 100;

    /** P0-4: 离线消息缓存 TTL（秒），默认 30 天（P0-3 从 7 天升级到 30 天） */
    public static final long WS_OFFLINE_TTL_SECONDS = 30 * 24 * 3600L;

    /** P0-3: Redis 离线消息溢出后的数据库持久化阈值（超过此数量时写入数据库） */
    public static final int WS_OFFLINE_DB_PERSIST_THRESHOLD = 50;

    /** P0-4: WebSocket 握手属性中的 userId key */
    public static final String WS_ATTR_USER_ID = "userId";

    /** P0-4: WebSocket 握手属性中的 username key */
    public static final String WS_ATTR_USERNAME = "username";

    /** P0-4: 握手请求中 JWT token 的查询参数名 */
    public static final String WS_TOKEN_PARAM = "token";

    /** P0-4: 握手请求中 JWT token 的请求头名 */
    public static final String WS_TOKEN_HEADER = "Authorization";
}
