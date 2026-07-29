package com.njydsz.message.domain.constant;

import com.njydsz.common.core.constant.SystemConstants;

/**
 * 消息通知引擎常量集中定义
 *
 * <p>集中管理消息模块的全局常量，包括默认值、Redis Key 前缀、幂等控制参数、
 * 限流配置、回执超时等。避免在业务代码中硬编码魔法值。
 *
 * <p><b>常量分组：</b>
 * <ul>
 *   <li><b>默认值</b>：DEFAULT_TENANT_ID / DEFAULT_LOCALE / DEFAULT_BIZ_TYPE</li>
 *   <li><b>幂等控制</b>：IDEMPOTENT_KEY_PREFIX / IDEMPOTENT_TTL_SECONDS</li>
 *   <li><b>Redis Key 前缀</b>：消息计数 / 已读状态 / 离线缓存 / 实时推送</li>
 *   <li><b>限流与重试</b>：默认重试次数 / 退避基数 / 最大退避时间</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class MessageConstants {

    private MessageConstants() {
    }

    /** 默认租户 ID（委托 {@link SystemConstants#DEFAULT_TENANT_ID}） */
    public static final String DEFAULT_TENANT_ID = SystemConstants.DEFAULT_TENANT_ID;

    /** 默认语言（委托 {@link SystemConstants#DEFAULT_LOCALE}） */
    public static final String DEFAULT_LOCALE = SystemConstants.DEFAULT_LOCALE;

    /** 默认业务类型（偏好表占位） */
    public static final String DEFAULT_BIZ_TYPE = "__DEFAULT__";

    /** 消费幂等 key 前缀 */
    public static final String IDEMPOTENT_KEY_PREFIX = "ydsz:msg:idempotent:";

    /** 消费幂等 TTL（秒），覆盖 RocketMQ 重投窗口 */
    public static final long IDEMPOTENT_TTL_SECONDS = 600L;

    /** 限流 key 前缀 */
    public static final String RATE_LIMIT_KEY_PREFIX = "ydsz:msg:ratelimit:";

    /** P2-5: 多维度限流 key 前缀 */
    public static final String RATE_LIMIT_RECEIVER_PREFIX = "ydsz:msg:ratelimit:receiver:";
    public static final String RATE_LIMIT_TEMPLATE_PREFIX = "ydsz:msg:ratelimit:template:";
    public static final String RATE_LIMIT_TENANT_PREFIX = "ydsz:msg:ratelimit:tenant:";

    /** P2-1: 智能去重 key 前缀（SET NX EX 原子去重） */
    public static final String DEDUP_KEY_PREFIX = "ydsz:msg:dedup:";

    /** 聚合批次锁前缀 */
    public static final String AGGREGATE_LOCK_PREFIX = "ydsz:msg:aggregate:lock:";

    /** 路由规则缓存 key */
    public static final String ROUTE_RULE_CACHE_KEY = "ydsz:msg:route:rules";

    /** 灰度桶缓存 key 前缀 */
    public static final String CANARY_CACHE_PREFIX = "ydsz:msg:canary:";

    /** 频率统计 key 前缀（每日 / 每小时） */
    public static final String FREQUENCY_DAILY_PREFIX = "ydsz:msg:freq:daily:";
    public static final String FREQUENCY_HOURLY_PREFIX = "ydsz:msg:freq:hourly:";

    /** 消息发送超时（毫秒） */
    public static final long SEND_TIMEOUT_MS = 5000L;

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 重试基础退避（毫秒），实际退避 = base * 2^retryCount */
    public static final long RETRY_BASE_BACKOFF_MS = 2000L;

    /** 重试扫描分布式锁 key */
    public static final String RETRY_SCAN_LOCK_KEY = "ydsz:msg:retry:scan:lock";

    /** 聚合批次扫描分布式锁 key (P2-4: 多实例部署保证唯一执行) */
    public static final String AGGREGATE_SCAN_LOCK_KEY = "ydsz:msg:aggregate:scan:lock";

    /** 单次重试扫描批量大小 */
    public static final int RETRY_SCAN_BATCH_SIZE = 200;

    /** 单次批量发送最大条数(防止阻塞过久) */
    public static final int BATCH_SEND_MAX_SIZE = 100;

    /** P2-6: 级联发送最大深度(防止无限递归,顶层消息深度=0) */
    public static final int MAX_CASCADE_DEPTH = 5;

    /** P2-9: 回执拉取调度器分布式锁 key */
    public static final String RECEIPT_PULL_LOCK_KEY = "ydsz:msg:receipt:pull:lock";

    /** P2-9: 单次回执拉取扫描批量大小 */
    public static final int RECEIPT_PULL_BATCH_SIZE = 200;

    /** P2-9: 回执拉取延迟阈值(分钟): 发送成功后多少分钟才开始主动拉取回执(给服务商回调留窗口) */
    public static final long RECEIPT_PULL_DELAY_MINUTES = 5L;

    /** P2-9: 回执超时阈值(分钟): 超过此时间仍未收到回执则标记为 TIMEOUT */
    public static final long RECEIPT_TIMEOUT_MINUTES = 30L;

    // WebSocket 相关常量已迁移到 com.njydsz.common.socket.constant.WebSocketConstants
}
