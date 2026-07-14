package com.njydsz.pmis.message.server.config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 消息引擎全局配置（prefix = {@code pmis.message}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code pmis.message.*} 配置项，
 * 包含通道开关、默认优先级、聚合 / 重试扫描间隔、全局频率上限、
 * 多维度限流（P2-5: receiver/templateCode/tenant）等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "pmis.message")
public class MessageProperties {

    /** 通道全局开关：key 为通道大写名（SMS/EMAIL/...），value 为是否启用 */
    private Map<String, Boolean> channelEnabled;

    /** 默认发送优先级 */
    private String defaultPriority = "NORMAL";

    /** 聚合扫描间隔（毫秒） */
    private long aggregateScanIntervalMs = 60000L;

    /** 重试扫描间隔（毫秒） */
    private long retryScanIntervalMs = 30000L;

    /**
     * P2-5: 消息 TTL（秒），超过此时间的消息自动丢弃。
     *
     * <p>用于消费者侧判断定时消息是否错过发送窗口：若 {@code scheduledAt} 距今
     * 超过此阈值，则视为过期消息直接跳过（如服务宕机恢复后积压的定时消息）。
     * 默认 3600s（1 小时），0 表示不检查 TTL。
     */
    private long messageTtlSeconds = 3600L;

    /**
     * P2-6: markAllRead 分批处理单批大小。
     *
     * <p>用户「全部已读」操作时，若未读通知量巨大（如万级），单条 UPDATE 会导致
     * 长事务与行锁堆积。改为按此批次大小循环 UPDATE，每批独立事务，避免长事务。
     * 默认 500，范围建议 200~1000。
     */
    private int markAllReadBatchSize = 500;

    /** P2-9: 回执拉取开关（关闭后不再主动拉取回执，仅依赖服务商回调） */
    private boolean receiptPullEnabled = true;

    /** P2-9: 回执拉取扫描间隔（毫秒），默认 120s */
    private long receiptPullScanIntervalMs = 120000L;

    /** P2-9: 回执拉取延迟阈值（分钟）：发送成功后多少分钟才开始主动拉取 */
    private long receiptPullDelayMinutes = 5L;

    /** P2-9: 回执超时阈值（分钟）：超过此时间仍未收到回执则标记为 TIMEOUT */
    private long receiptTimeoutMinutes = 30L;

    /** 全局每日发送上限（单用户单通道，0 表示不限） */
    private int globalDailyLimit = 0;

    /** 全局每小时发送上限（单用户单通道，0 表示不限） */
    private int globalHourlyLimit = 0;

    /** P2-5: 多维度限流配置 */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /** P2-1: 智能去重配置 */
    private DedupConfig dedup = new DedupConfig();

    /** P2-4: 成本看板配置 */
    private CostConfig cost = new CostConfig();

    /** P0-1: 短信服务商配置 */
    private SmsConfig sms = new SmsConfig();

    /**
     * 多维度限流配置（P2-5）。
     *
     * <p>支持 receiver / templateCode / tenant 三个维度的令牌桶限流，
     * 各维度独立配置 permits（每秒令牌数），任一维度超限即拒绝发送。
     * 维度间为 AND 关系：所有启用的维度都通过才允许发送。
     */
    @Data
    public static class RateLimitConfig {
        /** receiver 维度限流开关（避免同一接收人被轰炸） */
        private boolean receiverEnabled = true;
        /** receiver 维度每秒令牌数（同一 receiver 每秒最多发送条数） */
        private int receiverPermits = 10;

        /** templateCode 维度限流开关（避免单一模板占满配额） */
        private boolean templateEnabled = true;
        /** templateCode 维度每秒令牌数 */
        private int templatePermits = 100;

        /** tenant 维度限流开关（多租户配额隔离） */
        private boolean tenantEnabled = true;
        /** tenant 维度每秒令牌数 */
        private int tenantPermits = 1000;
    }

    /**
     * 智能去重配置（P2-1）。
     *
     * <p>基于 Redis {@code SET NX EX} 原子操作实现短窗口去重：相同 dedupKey 的消息
     * 在 {@code ttlSeconds} 秒内仅允许发送一次，超时后自动释放（允许补发）。
     * 适用于网络重试、上游重复触发、MQ 重投等场景，避免用户收到重复通知。
     *
     * <p>降级策略：Redis 不可用时自动放行（fail-open），避免阻断业务。
     */
    @Data
    public static class DedupConfig {
        /** 去重总开关（关闭后所有消息直接放行，不检查 Redis） */
        private boolean enabled = true;
        /** 去重窗口（秒）：同一 dedupKey 在此时间内视为重复，默认 60s */
        private int ttlSeconds = 60;
    }

    /**
     * 成本看板配置（P2-4）。
     *
     * <p>按通道配置单条消息成本（元），用于发送成本统计与看板展示。
     * SMS/EMAIL/PUSH 有实际服务商计费,INAPP/WEBHOOK/IM 通道免费。
     * 关闭后不记录成本字段（cost 始终为 0）。
     */
    @Data
    public static class CostConfig {
        /** 成本追踪开关（关闭后 cost 始终为 0） */
        private boolean enabled = true;
        /** 通道单条成本（元），key 为通道大写名 */
        private Map<String, BigDecimal> unitPrices = defaultUnitPrices();

        private static Map<String, BigDecimal> defaultUnitPrices() {
            // 使用 LinkedHashMap 保持插入顺序,使成本看板输出顺序稳定且可测试
            Map<String, BigDecimal> m = new LinkedHashMap<>();
            m.put("SMS", new BigDecimal("0.0450"));
            m.put("EMAIL", new BigDecimal("0.0010"));
            m.put("PUSH", new BigDecimal("0.0001"));
            m.put("INAPP", BigDecimal.ZERO);
            m.put("WEBHOOK", BigDecimal.ZERO);
            m.put("DINGTALK", BigDecimal.ZERO);
            m.put("WECOM", BigDecimal.ZERO);
            m.put("WECOM_APP", BigDecimal.ZERO);
            m.put("FEISHU", BigDecimal.ZERO);
            m.put("WX_MINI", BigDecimal.ZERO);
            m.put("ALIPAY_MINI", BigDecimal.ZERO);
            return m;
        }
    }

    /**
     * P0-1: 短信服务商配置。
     *
     * <p>通过 {@code pmis.message.sms.provider} 选择服务商（aliyun/mock），
     * 无凭证或选 mock 时降级为日志输出，保证开发环境可运行。
     */
    @Data
    public static class SmsConfig {
        /** 服务商: aliyun / mock（默认 mock 降级） */
        private String provider = "mock";
        /** 阿里云 SMS 配置 */
        private AliyunSmsConfig aliyun = new AliyunSmsConfig();
    }

    /**
     * 阿里云 SMS 配置。
     */
    @Data
    public static class AliyunSmsConfig {
        /** AccessKey ID */
        private String accessKeyId;
        /** AccessKey Secret */
        private String accessKeySecret;
        /** 默认签名（模板未配置时回退） */
        private String signName;
        /** 阿里云 SMS endpoint */
        private String endpoint = "dysmsapi.aliyuncs.com";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /** P0-2: APP 推送服务商配置 */
    private PushConfig push = new PushConfig();

    /** P0-1: 微信小程序订阅消息配置 */
    private WxMiniConfig wxMini = new WxMiniConfig();

    /** P0-1: 支付宝小程序模板消息配置 */
    private AlipayMiniConfig alipayMini = new AlipayMiniConfig();

    /**
     * P0-2: APP 推送服务商配置。
     *
     * <p>通过 {@code pmis.message.push.provider} 选择服务商（getui/mock），
     * 无凭证或选 mock 时降级为日志输出。
     */
    @Data
    public static class PushConfig {
        /** 服务商: getui / mock（默认 mock 降级） */
        private String provider = "mock";
        /** 个推配置 */
        private GetuiPushConfig getui = new GetuiPushConfig();
    }

    /**
     * 个推（GeTui）推送配置。
     */
    @Data
    public static class GetuiPushConfig {
        /** 个推 AppID */
        private String appId;
        /** 个推 AppKey */
        private String appKey;
        /** 个推 MasterSecret */
        private String masterSecret;
        /** 个推 REST API base url */
        private String baseUrl = "https://restapi.getui.com";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /** P1-4: 死信告警配置 */
    private DeadLetterAlertConfig deadLetterAlert = new DeadLetterAlertConfig();

    /** P1-7: 默认重试策略（全局兜底） */
    private RetryPolicy defaultRetryPolicy = new RetryPolicy();

    /**
     * P1-7: 按通道覆盖的重试策略。
     *
     * <p>key 为通道大写名（SMS/EMAIL/PUSH/...），value 为该通道专属重试策略。
     * 未命中的通道回退到 {@link #defaultRetryPolicy}。
     */
    private Map<String, RetryPolicy> channelRetryPolicies;

    /**
     * P1-7: 重试策略配置。
     *
     * <p>支持最大重试次数、基础退避、退避倍率、退避上限。退避公式：
     * {@code backoff = min(baseBackoffMs * backoffMultiplier^retryCount, maxBackoffMs)}。
     *
     * <p>默认值与原 {@code MessageConstants.MAX_RETRY_COUNT=3} /
     * {@code RETRY_BASE_BACKOFF_MS=2000} 保持等价（倍率 2.0，上限 60s），
     * 确保不配置时行为不变。
     */
    @Data
    public static class RetryPolicy {
        /** 最大重试次数（达到后转死信/失败） */
        private int maxRetryCount = 3;
        /** 基础退避（毫秒） */
        private long baseBackoffMs = 2000L;
        /** 退避倍率（指数退避底数，默认 2.0） */
        private double backoffMultiplier = 2.0;
        /** 退避上限（毫秒，防止单次退避过大） */
        private long maxBackoffMs = 60000L;
    }

    /**
     * P1-4: 死信告警配置。
     *
     * <p>当指定时间窗口内某通道死信数量达到阈值时触发告警事件
     * ({@link com.njydsz.pmis.message.server.event.DeadLetterAlertEvent})，
     * 通过 {@code cooldownMinutes} 控制同一通道告警冷却，避免告警风暴。
     */
    @Data
    public static class DeadLetterAlertConfig {
        /** 死信告警开关（关闭后仅落库不告警） */
        private boolean enabled = true;
        /** 告警阈值：窗口内死信数达到此值触发告警 */
        private int threshold = 10;
        /** 统计窗口（分钟） */
        private int windowMinutes = 60;
        /** 告警冷却（分钟）：同一通道告警后多久内不重复告警 */
        private int cooldownMinutes = 30;
    }

    /** P1-5: 退订中心配置 */
    private UnsubscribeConfig unsubscribe = new UnsubscribeConfig();

    /**
     * P0-1: 微信小程序订阅消息配置。
     *
     * <p>通过 {@code pmis.message.wx-mini.provider} 选择服务商（wechat/mock），
     * 无凭证或选 mock 时降级为日志输出。
     * 微信小程序订阅消息需要用户在小程序端主动订阅后才能下发，
     * 每次发送消耗一次订阅配额。
     */
    @Data
    public static class WxMiniConfig {
        /** 服务商: wechat / mock（默认 mock 降级） */
        private String provider = "mock";
        /** 微信小程序 AppID */
        private String appId;
        /** 微信小程序 AppSecret */
        private String appSecret;
        /** 微信 API base URL */
        private String baseUrl = "https://api.weixin.qq.com";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /**
     * P0-1: 支付宝小程序模板消息配置。
     *
     * <p>通过 {@code pmis.message.alipay-mini.provider} 选择服务商（alipay/mock），
     * 无凭证或选 mock 时降级为日志输出。
     */
    @Data
    public static class AlipayMiniConfig {
        /** 服务商: alipay / mock（默认 mock 降级） */
        private String provider = "mock";
        /** 支付宝小程序 AppID */
        private String appId;
        /** 支付宝应用私钥 */
        private String privateKey;
        /** 支付宝公钥 */
        private String alipayPublicKey;
        /** 支付宝网关地址 */
        private String gateway = "https://openapi.alipay.com/gateway.do";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /** P2-5: 智能定时配置 */
    private SmartTimingConfig smartTiming = new SmartTimingConfig();

    /**
     * P1-5: 退订中心配置。
     *
     * <p>支持 token-based 一键退订（RFC 8058 List-Unsubscribe-Post），
     * token 采用 HMAC-SHA256 签名，{@code ttlDays} 控制链接有效期（默认 30 天，
     * 符合邮件退订链接的最佳实践）。{@code secret} 必须配置为 ≥32 字节的随机串，
     * 未配置时降级使用一个内置默认值（仅开发环境，生产必须覆盖）。
     */
    @Data
    public static class UnsubscribeConfig {
        /** 退订中心总开关（关闭后 token 一键退订接口拒绝执行） */
        private boolean enabled = true;
        /** token 签名密钥（Base64 编码，建议 ≥32 字节随机串；为空时使用内置默认值） */
        private String secret;
        /** token 有效期（天），默认 30 天 */
        private int ttlDays = 30;
        /** 退订链接 base URL（如 https://pmis.example.com/unsubscribe），用于拼接完整链接 */
        private String baseUrl;
    }

    /**
     * P2-5: 智能定时配置。
     *
     * <p>超越简单 DND 拦截的智能发送时机策略：
     * <ul>
     *   <li>DND 命中时不再丢弃消息，而是<strong>延迟到 DND 结束后</strong>自动重发</li>
     *   <li>URGENT 优先级消息可绕过 DND 立即发送</li>
     *   <li>DND 仅对"打扰型"通道生效（SMS/PUSH/IM），EMAIL/INAPP/Webhook 不受 DND 限制</li>
     * </ul>
     */
    @Data
    public static class SmartTimingConfig {
        /** 智能定时总开关（关闭后 DND 命中仍走旧的丢弃策略） */
        private boolean enabled = true;
        /** URGENT 优先级是否绕过 DND（默认 true，紧急消息必须立即送达） */
        private boolean urgentBypassDnd = true;
        /** DND 生效的打扰型通道列表（默认 SMS/PUSH/DINGTALK/WECOM/FEISHU） */
        private List<String> disruptiveChannels = Arrays.asList(
                "SMS", "PUSH", "DINGTALK", "WECOM", "FEISHU", "WX_MINI", "ALIPAY_MINI");
        /** DND 延迟发送时附加的缓冲秒数（默认 60s，避免卡在 DND 结束瞬间的高峰） */
        private long dndBufferSeconds = 60L;
        /** DND 延迟消息最大延迟小时数（超过则降级为丢弃，防止消息过期太久失去意义，默认 72h） */
        private long maxDeferHours = 72L;

        /**
         * 判断指定通道是否为打扰型通道（受 DND 约束）。
         *
         * @param channel 通道名称（大写）
         * @return true 表示该通道受 DND 约束
         */
        public boolean isDisruptive(String channel) {
            if (channel == null) {
                return false;
            }
            return disruptiveChannels.contains(channel.toUpperCase());
        }

        /**
         * 获取打扰型通道集合（用于测试与诊断）。
         *
         * @return 不可变副本
         */
        public Set<String> disruptiveChannelSet() {
            return new LinkedHashSet<>(disruptiveChannels);
        }
    }
}
