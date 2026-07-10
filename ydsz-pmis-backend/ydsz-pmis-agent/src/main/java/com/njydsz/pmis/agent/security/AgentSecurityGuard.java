package com.njydsz.pmis.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Agent 安全合规守卫（P2-10 落地，P1-1 增强 Redis 分布式限流，P2-9 增强可配置敏感词）。
 *
 * <p>对标 Coze 内容审核 / Dify 审核模式 / OpenAI Moderation API：
 * <ul>
 *   <li><b>输出审核</b>：对 LLM 输出进行敏感词检测和内容过滤（支持可配置 + 正则变体匹配）</li>
 *   <li><b>Rate Limit</b>：基于 Redis 的分布式滑动窗口限流，多实例部署全局生效</li>
 *   <li><b>审计日志</b>：记录所有 Agent 调用的输入、输出、调用者信息</li>
 * </ul>
 *
 * <p><b>P1-1 修复</b>：原 {@code ConcurrentHashMap} 单机限流在 K8s 多副本部署下每个 Pod 独立计数，
 * 限流形同虚设。现改为 Redis INCR + EXPIRE 实现分布式固定窗口限流。
 *
 * <p><b>P2-9 修复</b>：原敏感词列表硬编码在代码中，无法运行时调整。现改为 Nacos 配置驱动，
 * 支持运行时热更新敏感词列表；同时增加正则变体匹配，识别如 {@code p@ssword}、{@code pass word} 等变体。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0 (P2-10), 1.3.1 (P1-1 + P2-9)
 */
@Slf4j
@Component
public class AgentSecurityGuard {

    /** 默认 Rate Limit：每分钟最大请求数 */
    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 30;

    /** Redis 限流 Key 前缀 */
    private static final String RATE_LIMIT_KEY_PREFIX = "pmis:agent:ratelimit:";

    /** 默认敏感词列表（当 Nacos 未配置时使用） */
    private static final Set<String> DEFAULT_SENSITIVE_WORDS = Set.of(
            "密码", "password", "secret", "token", "credential",
            "身份证", "手机号", "银行卡", "CVV", "PIN"
    );

    /** 默认敏感词变体正则（用于识别 p@ssword、pass word 等变体） */
    private static final List<Pattern> DEFAULT_SENSITIVE_PATTERNS = List.of(
            // password 变体：p@ssw0rd, p@ss word, p.a.s.s.w.o.r.d 等
            Pattern.compile("(?i)p[\\s._@-]*a[\\s._@-]*s[\\s._@-]*s[\\s._@-]*w[\\s._@-]*[o0][\\s._@-]*r[\\s._@-]*d"),
            // secret 变体
            Pattern.compile("(?i)s[\\s._@-]*e[\\s._@-]*c[\\s._@-]*r[\\s._@-]*e[\\s._@-]*t"),
            // token 变体
            Pattern.compile("(?i)t[\\s._@-]*[o0][\\s._@-]*k[\\s._@-]*e[\\s._@-]*n"),
            // credential 变体
            Pattern.compile("(?i)c[\\s._@-]*r[\\s._@-]*e[\\s._@-]*d[\\s._@-]*e[\\s._@-]*n[\\s._@-]*t[\\s._@-]*i[\\s._@-]*a[\\s._@-]*l"),
            // 身份证号格式（18位，末位X）
            Pattern.compile("\\d{6}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]"),
            // 手机号格式（11位）
            Pattern.compile("1[3-9]\\d{9}"),
            // 银行卡号格式（16-19位连续数字）
            Pattern.compile("\\d{16,19}"),
            // CVV 格式（3-4位数字紧跟在 CVV 附近）
            Pattern.compile("(?i)cvv[\\s:]*\\d{3,4}")
    );

    private final StringRedisTemplate redisTemplate;

    @Value("${pmis.agent.security.rate-limit:" + DEFAULT_RATE_LIMIT_PER_MINUTE + "}")
    private int rateLimitPerMinute;

    @Value("${pmis.agent.security.content-filter-enabled:true}")
    private boolean contentFilterEnabled;

    /**
     * 可配置的敏感词列表（逗号分隔，通过 Nacos 热更新）。
     * <p>为空时使用 {@link #DEFAULT_SENSITIVE_WORDS}。
     */
    @Value("${pmis.agent.security.sensitive-words:}")
    private String sensitiveWordsConfig;

    /**
     * 是否启用正则变体匹配（默认开启）。
     * <p>正则匹配比纯字符串包含匹配开销大，可通过配置关闭。
     */
    @Value("${pmis.agent.security.regex-pattern-enabled:true}")
    private boolean regexPatternEnabled;

    /**
     * 构造函数注入 Redis 模板。
     *
     * @param redisTemplate Redis 响应式模板（用于分布式限流计数）
     */
    public AgentSecurityGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 输出审核 ====================

    /**
     * 审核 LLM 输出内容。
     *
     * <p>检测策略（P2-9 增强）：
     * <ol>
     *   <li>精确字符串匹配（可配置敏感词列表）</li>
     *   <li>正则变体匹配（识别 p@ssword、pass word、身份证号等变体）</li>
     * </ol>
     *
     * @param content LLM 输出内容
     * @return 审核结果
     */
    public ContentFilterResult filterContent(String content) {
        if (!contentFilterEnabled) {
            return ContentFilterResult.pass();
        }
        if (content == null || content.isBlank()) {
            return ContentFilterResult.pass();
        }

        // 1. 精确字符串匹配
        Set<String> words = resolveSensitiveWords();
        String lowerContent = content.toLowerCase();
        for (String word : words) {
            if (lowerContent.contains(word.toLowerCase())) {
                log.warn("[Security] 输出内容包含敏感词: {}", word);
                return ContentFilterResult.block("输出内容包含敏感信息: " + word, word);
            }
        }

        // 2. 正则变体匹配（P2-9）
        if (regexPatternEnabled) {
            for (Pattern pattern : DEFAULT_SENSITIVE_PATTERNS) {
                if (pattern.matcher(content).find()) {
                    log.warn("[Security] 输出内容匹配敏感模式: {}", pattern.pattern());
                    return ContentFilterResult.block(
                            "输出内容包含敏感信息变体: " + pattern.pattern(), pattern.pattern());
                }
            }
        }

        return ContentFilterResult.pass();
    }

    /**
     * 对输出内容进行脱敏处理。
     *
     * @param content 原始内容
     * @return 脱敏后的内容
     */
    public String maskSensitiveData(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String masked = content;
        Set<String> words = resolveSensitiveWords();
        for (String word : words) {
            if (masked.toLowerCase().contains(word.toLowerCase())) {
                masked = masked.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
            }
        }
        // 正则模式脱敏
        if (regexPatternEnabled) {
            for (Pattern pattern : DEFAULT_SENSITIVE_PATTERNS) {
                masked = pattern.matcher(masked).replaceAll("***");
            }
        }
        return masked;
    }

    /**
     * 解析当前生效的敏感词集合。
     * <p>优先使用 Nacos 配置的敏感词列表；未配置时使用默认列表。
     *
     * @return 敏感词集合
     */
    private Set<String> resolveSensitiveWords() {
        if (sensitiveWordsConfig != null && !sensitiveWordsConfig.isBlank()) {
            Set<String> configured = java.util.Arrays.stream(sensitiveWordsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
            if (!configured.isEmpty()) {
                return configured;
            }
        }
        return DEFAULT_SENSITIVE_WORDS;
    }

    // ==================== Rate Limit (Redis 分布式) ====================

    /**
     * 检查请求频率是否超限（P1-1：基于 Redis 的分布式固定窗口限流）。
     *
     * <p>使用 Redis INCR + EXPIRE 实现分布式计数：
     * <ol>
     *   <li>Key 格式：{@code pmis:agent:ratelimit:{callerId}:{minuteTimestamp}}</li>
     *   <li>首次请求：INCR 返回 1，设置 EXPIRE 为 60s</li>
     *   <li>后续请求：INCR 递增，若超过阈值则拒绝</li>
     *   <li>Key 在 60s 后自动过期，下一分钟窗口自动重置</li>
     * </ol>
     *
     * <p>多实例部署下所有 Pod 共享 Redis 计数，限流全局生效。
     *
     * @param callerId 调用者 ID
     * @return true 表示允许请求，false 表示被限流
     */
    public boolean checkRateLimit(String callerId) {
        if (callerId == null || callerId.isBlank()) {
            callerId = "anonymous";
        }

        long currentMinute = System.currentTimeMillis() / 60_000;
        String key = RATE_LIMIT_KEY_PREFIX + callerId + ":" + currentMinute;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // 首次请求，设置过期时间（60s + 5s 缓冲，防止边界过期）
                redisTemplate.expire(key, Duration.ofSeconds(65));
            }

            if (count != null && count > rateLimitPerMinute) {
                log.warn("[Security] Rate Limit 触发 (Redis): caller={}, count={}, limit={}",
                        callerId, count, rateLimitPerMinute);
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 不可用时降级为放行（避免 Redis 故障导致服务不可用）
            log.error("[Security] Redis 限流检查异常, 降级放行: caller={}, error={}", callerId, e.getMessage());
            return true;
        }
    }

    /**
     * 获取指定调用者的当前分钟请求数（从 Redis 读取）。
     *
     * @param callerId 调用者 ID
     * @return 当前分钟请求数；Redis 异常时返回 -1
     */
    public long getCurrentCount(String callerId) {
        if (callerId == null || callerId.isBlank()) {
            callerId = "anonymous";
        }
        long currentMinute = System.currentTimeMillis() / 60_000;
        String key = RATE_LIMIT_KEY_PREFIX + callerId + ":" + currentMinute;
        try {
            String val = redisTemplate.opsForValue().get(key);
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            log.warn("[Security] 读取限流计数异常: {}", e.getMessage());
            return -1;
        }
    }

    // ==================== 审计日志 ====================

    /**
     * 记录审计日志。
     *
     * @param callerId   调用者 ID
     * @param agentType  Agent 类型
     * @param input      输入摘要
     * @param output     输出摘要
     * @param success    是否成功
     */
    public void auditLog(String callerId, String agentType, String input,
                          String output, boolean success) {
        log.info("[Audit] caller={}, agent={}, success={}, inputLen={}, outputLen={}",
                callerId, agentType, success,
                input != null ? input.length() : 0,
                output != null ? output.length() : 0);
    }

    // ==================== 内部类 ====================

    /**
     * 内容审核结果。
     */
    public static class ContentFilterResult {
        private final boolean passed;
        private final String reason;
        private final String blockedWord;

        private ContentFilterResult(boolean passed, String reason, String blockedWord) {
            this.passed = passed;
            this.reason = reason;
            this.blockedWord = blockedWord;
        }

        public static ContentFilterResult pass() {
            return new ContentFilterResult(true, null, null);
        }

        public static ContentFilterResult block(String reason, String blockedWord) {
            return new ContentFilterResult(false, reason, blockedWord);
        }

        public boolean isPassed() { return passed; }
        public String getReason() { return reason; }
        public String getBlockedWord() { return blockedWord; }
    }
}
