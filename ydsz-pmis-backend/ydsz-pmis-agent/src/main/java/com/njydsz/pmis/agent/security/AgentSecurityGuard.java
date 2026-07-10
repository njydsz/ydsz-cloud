package com.njydsz.pmis.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 安全合规守卫（P2-10 落地）。
 *
 * <p>对标 Coze 内容审核 / Dify 审核模式 / OpenAI Moderation API：
 * <ul>
 *   <li><b>输出审核</b>：对 LLM 输出进行敏感词检测和内容过滤</li>
 *   <li><b>Rate Limit</b>：按用户/IP/Agent 维度的请求频率限制</li>
 *   <li><b>审计日志</b>：记录所有 Agent 调用的输入、输出、调用者信息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0 (P2-10)
 */
@Slf4j
@Component
public class AgentSecurityGuard {

    /** 默认 Rate Limit：每分钟最大请求数 */
    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 30;

    /** 敏感词列表 */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "密码", "password", "secret", "token", "credential",
            "身份证", "手机号", "银行卡", "CVV", "PIN"
    );

    /** Rate Limit：callerId → 当前分钟的时间戳 → 请求计数 */
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    @Value("${pmis.agent.security.rate-limit:" + DEFAULT_RATE_LIMIT_PER_MINUTE + "}")
    private int rateLimitPerMinute;

    @Value("${pmis.agent.security.content-filter-enabled:true}")
    private boolean contentFilterEnabled;

    // ==================== 输出审核 ====================

    /**
     * 审核 LLM 输出内容。
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

        String lowerContent = content.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            if (lowerContent.contains(word.toLowerCase())) {
                log.warn("[Security] 输出内容包含敏感词: {}", word);
                return ContentFilterResult.block("输出内容包含敏感信息: " + word, word);
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
        for (String word : SENSITIVE_WORDS) {
            if (masked.toLowerCase().contains(word.toLowerCase())) {
                // 替换为 ***
                masked = masked.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
            }
        }
        return masked;
    }

    // ==================== Rate Limit ====================

    /**
     * 检查请求频率是否超限。
     *
     * @param callerId 调用者 ID
     * @return true 表示允许请求，false 表示被限流
     */
    public boolean checkRateLimit(String callerId) {
        if (callerId == null || callerId.isBlank()) {
            callerId = "anonymous";
        }

        long currentMinute = System.currentTimeMillis() / 60_000;
        RateLimitEntry entry = rateLimitMap.compute(callerId, (key, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new RateLimitEntry(currentMinute, new AtomicLong(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        long count = entry.count.get();
        if (count > rateLimitPerMinute) {
            log.warn("[Security] Rate Limit 触发: caller={}, count={}, limit={}",
                    callerId, count, rateLimitPerMinute);
            return false;
        }
        return true;
    }

    /**
     * 获取当前 Rate Limit 统计。
     *
     * @return callerId → 当前分钟请求数
     */
    public ConcurrentHashMap<String, RateLimitEntry> getRateLimitStats() {
        return rateLimitMap;
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
     * Rate Limit 计数条目。
     */
    public static class RateLimitEntry {
        public final long minute;
        public final AtomicLong count;

        RateLimitEntry(long minute, AtomicLong count) {
            this.minute = minute;
            this.count = count;
        }

        public long getCount() {
            return count.get();
        }
    }

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
