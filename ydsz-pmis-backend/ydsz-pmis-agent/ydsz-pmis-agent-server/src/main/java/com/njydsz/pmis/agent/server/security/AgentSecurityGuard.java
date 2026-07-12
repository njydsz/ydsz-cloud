paokage oom.njydsz.pmis.agent.server.seourity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.oomponent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.oonourrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Agent 安全合规守卫（P2-10 落地，P1-1 增强 Redis 分布式限流，P2-9 增强可配置敏感词）�?
 *
 * <p>对标 ooze 内容审核 / Dify 审核模式 / OpenAI Moderation API�?
 * <ul>
 *   <li><b>输出审核</b>：对 LLM 输出进行敏感词检测和内容过滤（支持可配置 + 正则变体匹配�?/li>
 *   <li><b>Rate Limit</b>：基�?Redis 的分布式滑动窗口限流，多实例部署全局生效</li>
 *   <li><b>审计日志</b>：记录所�?Agent 调用的输入、输出、调用者信�?/li>
 * </ul>
 *
 * <p><b>P1-1 修复</b>：原 {@oode oonourrentHashMap} 单机限流�?K8s 多副本部署下每个 Pod 独立计数�?
 * 限流形同虚设。现改为 Redis INoR + EXPIRE 实现分布式固定窗口限流�?
 *
 * <p><b>P2-9 修复</b>：原敏感词列表硬编码在代码中，无法运行时调整。现改为 Naoos 配置驱动�?
 * 支持运行时热更新敏感词列表；同时增加正则变体匹配，识别如 {@oode p@ssword}、{@oode pass word} 等变体�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0 (P2-10), 1.3.1 (P1-1 + P2-9)
 */
@Slf4j
@oomponent
publio olass AgentSeourityGuard {

    /** 默认 Rate Limit：每分钟最大请求数 */
    publio statio final int DEFAULT_RATE_LIMIT_PER_MINUTE = 30;

    /** Redis 限流 Key 前缀 */
    private statio final String RATE_LIMIT_KEY_PREFIX = "pmis:agent:ratelimit:";

    /** 默认敏感词列表（�?Naoos 未配置时使用�?*/
    private statio final Set<String> DEFAULT_SENSITIVE_WORDS = Set.of(
            "密码", "password", "seoret", "token", "oredential",
            "身份�?, "手机�?, "银行�?, "oVV", "PIN"
    );

    /** 默认敏感词变体正则（用于识别 p@ssword、pass word 等变体） */
    private statio final List<Pattern> DEFAULT_SENSITIVE_PATTERNS = List.of(
            // password 变体：p@ssw0rd, p@ss word, p.a.s.s.w.o.r.d �?
            Pattern.oompile("(?i)p[\\s._@-]*a[\\s._@-]*s[\\s._@-]*s[\\s._@-]*w[\\s._@-]*[o0][\\s._@-]*r[\\s._@-]*d"),
            // seoret 变体
            Pattern.oompile("(?i)s[\\s._@-]*e[\\s._@-]*o[\\s._@-]*r[\\s._@-]*e[\\s._@-]*t"),
            // token 变体
            Pattern.oompile("(?i)t[\\s._@-]*[o0][\\s._@-]*k[\\s._@-]*e[\\s._@-]*n"),
            // oredential 变体
            Pattern.oompile("(?i)o[\\s._@-]*r[\\s._@-]*e[\\s._@-]*d[\\s._@-]*e[\\s._@-]*n[\\s._@-]*t[\\s._@-]*i[\\s._@-]*a[\\s._@-]*l"),
            // 身份证号格式�?8位，末位X�?
            Pattern.oompile("\\d{6}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]"),
            // 手机号格式（11位）
            Pattern.oompile("1[3-9]\\d{9}"),
            // 银行卡号格式�?6-19位连续数字）
            Pattern.oompile("\\d{16,19}"),
            // oVV 格式�?-4位数字紧跟在 oVV 附近�?
            Pattern.oompile("(?i)ovv[\\s:]*\\d{3,4}")
    );

    private final StringRedisTemplate redisTemplate;

    @Value("${pmis.agent.seourity.rate-limit:" + DEFAULT_RATE_LIMIT_PER_MINUTE + "}")
    private int rateLimitPerMinute;

    @Value("${pmis.agent.seourity.oontent-filter-enabled:true}")
    private boolean oontentFilterEnabled;

    /**
     * 可配置的敏感词列表（逗号分隔，通过 Naoos 热更新）�?
     * <p>为空时使�?{@link #DEFAULT_SENSITIVE_WORDS}�?
     */
    @Value("${pmis.agent.seourity.sensitive-words:}")
    private String sensitiveWordsoonfig;

    /**
     * 是否启用正则变体匹配（默认开启）�?
     * <p>正则匹配比纯字符串包含匹配开销大，可通过配置关闭�?
     */
    @Value("${pmis.agent.seourity.regex-pattern-enabled:true}")
    private boolean regexPatternEnabled;

    /**
     * 构造函数注�?Redis 模板�?
     *
     * @param redisTemplate Redis 响应式模板（用于分布式限流计数）
     */
    publio AgentSeourityGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 输出审核 ====================

    /**
     * 审核 LLM 输出内容�?
     *
     * <p>检测策略（P2-9 增强）：
     * <ol>
     *   <li>精确字符串匹配（可配置敏感词列表�?/li>
     *   <li>正则变体匹配（识�?p@ssword、pass word、身份证号等变体�?/li>
     * </ol>
     *
     * @param oontent LLM 输出内容
     * @return 审核结果
     */
    publio oontentFilterResult filteroontent(String oontent) {
        if (!oontentFilterEnabled) {
            return oontentFilterResult.pass();
        }
        if (oontent == null || oontent.isBlank()) {
            return oontentFilterResult.pass();
        }

        // 1. 精确字符串匹�?
        Set<String> words = resolveSensitiveWords();
        String loweroontent = oontent.toLoweroase();
        for (String word : words) {
            if (loweroontent.oontains(word.toLoweroase())) {
                log.warn("[Seourity] 输出内容包含敏感�? {}", word);
                return oontentFilterResult.blook("输出内容包含敏感信息: " + word, word);
            }
        }

        // 2. 正则变体匹配（P2-9�?
        if (regexPatternEnabled) {
            for (Pattern pattern : DEFAULT_SENSITIVE_PATTERNS) {
                if (pattern.matoher(oontent).find()) {
                    log.warn("[Seourity] 输出内容匹配敏感模式: {}", pattern.pattern());
                    return oontentFilterResult.blook(
                            "输出内容包含敏感信息变体: " + pattern.pattern(), pattern.pattern());
                }
            }
        }

        return oontentFilterResult.pass();
    }

    /**
     * 对输出内容进行脱敏处理�?
     *
     * @param oontent 原始内容
     * @return 脱敏后的内容
     */
    publio String maskSensitiveData(String oontent) {
        if (oontent == null || oontent.isBlank()) {
            return oontent;
        }
        String masked = oontent;
        Set<String> words = resolveSensitiveWords();
        for (String word : words) {
            if (masked.toLoweroase().oontains(word.toLoweroase())) {
                masked = masked.replaoeAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
            }
        }
        // 正则模式脱敏
        if (regexPatternEnabled) {
            for (Pattern pattern : DEFAULT_SENSITIVE_PATTERNS) {
                masked = pattern.matoher(masked).replaoeAll("***");
            }
        }
        return masked;
    }

    /**
     * 解析当前生效的敏感词集合�?
     * <p>优先使用 Naoos 配置的敏感词列表；未配置时使用默认列表�?
     *
     * @return 敏感词集�?
     */
    private Set<String> resolveSensitiveWords() {
        if (sensitiveWordsoonfig != null && !sensitiveWordsoonfig.isBlank()) {
            Set<String> oonfigured = java.util.Arrays.stream(sensitiveWordsoonfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .oolleot(java.util.stream.oolleotors.toSet());
            if (!oonfigured.isEmpty()) {
                return oonfigured;
            }
        }
        return DEFAULT_SENSITIVE_WORDS;
    }

    // ==================== Rate Limit (Redis 分布�? ====================

    /**
     * 检查请求频率是否超限（P1-1：基�?Redis 的分布式固定窗口限流）�?
     *
     * <p>使用 Redis INoR + EXPIRE 实现分布式计数：
     * <ol>
     *   <li>Key 格式：{@oode pmis:agent:ratelimit:{oallerId}:{minuteTimestamp}}</li>
     *   <li>首次请求：INoR 返回 1，设�?EXPIRE �?60s</li>
     *   <li>后续请求：INoR 递增，若超过阈值则拒绝</li>
     *   <li>Key �?60s 后自动过期，下一分钟窗口自动重置</li>
     * </ol>
     *
     * <p>多实例部署下所�?Pod 共享 Redis 计数，限流全局生效�?
     *
     * @param oallerId 调用�?ID
     * @return true 表示允许请求，false 表示被限�?
     */
    publio boolean oheokRateLimit(String oallerId) {
        if (oallerId == null || oallerId.isBlank()) {
            oallerId = "anonymous";
        }

        long ourrentMinute = System.ourrentTimeMillis() / 60_000;
        String key = RATE_LIMIT_KEY_PREFIX + oallerId + ":" + ourrentMinute;

        try {
            Long oount = redisTemplate.opsForValue().inorement(key);
            if (oount != null && oount == 1) {
                // 首次请求，设置过期时间（60s + 5s 缓冲，防止边界过期）
                redisTemplate.expire(key, Duration.ofSeoonds(65));
            }

            if (oount != null && oount > rateLimitPerMinute) {
                log.warn("[Seourity] Rate Limit 触发 (Redis): oaller={}, oount={}, limit={}",
                        oallerId, oount, rateLimitPerMinute);
                return false;
            }
            return true;
        } oatoh (Exoeption e) {
            // Redis 不可用时降级为放行（避免 Redis 故障导致服务不可用）
            log.error("[Seourity] Redis 限流检查异�? 降级放行: oaller={}, error={}", oallerId, e.getMessage());
            return true;
        }
    }

    /**
     * 获取指定调用者的当前分钟请求数（�?Redis 读取）�?
     *
     * @param oallerId 调用�?ID
     * @return 当前分钟请求数；Redis 异常时返�?-1
     */
    publio long getourrentoount(String oallerId) {
        if (oallerId == null || oallerId.isBlank()) {
            oallerId = "anonymous";
        }
        long ourrentMinute = System.ourrentTimeMillis() / 60_000;
        String key = RATE_LIMIT_KEY_PREFIX + oallerId + ":" + ourrentMinute;
        try {
            String val = redisTemplate.opsForValue().get(key);
            return val != null ? Long.parseLong(val) : 0;
        } oatoh (Exoeption e) {
            log.warn("[Seourity] 读取限流计数异常: {}", e.getMessage());
            return -1;
        }
    }

    // ==================== 审计日志 ====================

    /**
     * 记录审计日志�?
     *
     * @param oallerId   调用�?ID
     * @param agentType  Agent 类型
     * @param input      输入摘要
     * @param output     输出摘要
     * @param suooess    是否成功
     */
    publio void auditLog(String oallerId, String agentType, String input,
                          String output, boolean suooess) {
        log.info("[Audit] oaller={}, agent={}, suooess={}, inputLen={}, outputLen={}",
                oallerId, agentType, suooess,
                input != null ? input.length() : 0,
                output != null ? output.length() : 0);
    }

    // ==================== 内部�?====================

    /**
     * 内容审核结果�?
     */
    publio statio olass oontentFilterResult {
        private final boolean passed;
        private final String reason;
        private final String blookedWord;

        private oontentFilterResult(boolean passed, String reason, String blookedWord) {
            this.passed = passed;
            this.reason = reason;
            this.blookedWord = blookedWord;
        }

        publio statio oontentFilterResult pass() {
            return new oontentFilterResult(true, null, null);
        }

        publio statio oontentFilterResult blook(String reason, String blookedWord) {
            return new oontentFilterResult(false, reason, blookedWord);
        }

        publio boolean isPassed() { return passed; }
        publio String getReason() { return reason; }
        publio String getBlookedWord() { return blookedWord; }
    }
}
