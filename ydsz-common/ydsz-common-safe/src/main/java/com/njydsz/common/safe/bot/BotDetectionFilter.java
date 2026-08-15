package com.njydsz.common.safe.bot;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.util.http.UrlPathUtils;

/**
 * Bot / 爬虫识别过滤器
 *
 * <p>基于多信号综合评分识别自动化爬虫和恶意扫描器。不同于传统 UA 黑名单的简单匹配，
 * 本过滤器采用"评分制"（score-based detection），综合以下信号：
 * <ul>
 *   <li>User-Agent 合法性（是否为空、是否包含爬虫关键词、是否匹配浏览器特征）</li>
 *   <li>请求头完整性（正常浏览器通常携带 Accept-Language、Accept-Encoding 等）</li>
 *   <li>请求路径（是否为敏感路径扫描）</li>
 * </ul>
 *
 * <p>评分超过阈值时发布 {@code ILLEGAL_ACCESS} 安全事件，由下游
 * {@code SecurityEventAggregator} 决定是否触发自动封禁。
 *
 * <p><b>设计原则：</b>仅发布事件、不直接拦截。阻断决策由安全事件聚合器统一处理，
 * 避免 Bot 过滤器与 IP 封禁逻辑耦合。
 *
 * <p><b>启用条件：</b>通过 {@code ydsz.safe.bot-detection.enabled=true} 启用。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class BotDetectionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BotDetectionFilter.class);

    /** 爬虫/扫描器 UA 关键词（命中即判定为 Bot，无需评分） */
    private static final Pattern BOT_UA_PATTERN = Pattern.compile(
            "(?i)(bot|crawler|spider|scan|harvest|extract|scrape|curl|wget|python-requests|" +
            "httpclient|scrapy|nikto|sqlmap|nmap|masscan|zgrab|dirbuster|gobuster)",
            Pattern.CASE_INSENSITIVE
    );

    /** 合法浏览器 UA 必须包含的特征（Mozilla/5.0 是现代浏览器通用前缀） */
    private static final Pattern BROWSER_UA_PATTERN = Pattern.compile(
            "(?i)(Mozilla/5\\.0|AppleWebKit|Chrome|Safari|Firefox|Edge|Opera)",
            Pattern.CASE_INSENSITIVE
    );

    /** 评分阈值（>= 此值判定为 Bot） */
    private static final int SCORE_THRESHOLD = 70;

    /** 空/缺失 UA 评分 */
    private static final int SCORE_MISSING_UA = 50;

    /** 爬虫关键词 UA 评分（直接判定） */
    private static final int SCORE_BOT_UA = 100;

    /** 缺少浏览器特征评分 */
    private static final int SCORE_NO_BROWSER_FEATURE = 40;

    /** 缺少 Accept-Language 评分 */
    private static final int SCORE_NO_ACCEPT_LANGUAGE = 20;

    /** 缺少 Accept-Encoding 评分 */
    private static final int SCORE_NO_ACCEPT_ENCODING = 10;

    private static final Set<String> SENSITIVE_PATHS = Set.of(
            "/admin", "/wp-admin", "/phpmyadmin", "/.env", "/.git", "/actuator",
            "/config", "/backup", "/shell", "/console"
    );

    private final SecurityEventPublisher eventPublisher;
    private final Set<String> excludes;

    /**
     * @param eventPublisher 安全事件发布器
     * @param excludes       排除路径（Ant 风格）
     */
    public BotDetectionFilter(SecurityEventPublisher eventPublisher, Set<String> excludes) {
        this.eventPublisher = eventPublisher;
        this.excludes = excludes != null ? excludes : new HashSet<>();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 排除路径直接放行
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 仅对 GET 请求（爬虫主要使用 GET 扫描）进行 Bot 检测
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        int score = calculateBotScore(request);
        if (score >= SCORE_THRESHOLD) {
            String ua = request.getHeader("User-Agent");
            String clientIp = ClientIpResolver.getClientIp(request);
            log.debug("Bot 检测高评分 | ip={} | ua={} | score={}", clientIp, ua, score);

            // 发布安全事件（由 SecurityEventAggregator 决定是否封禁）
            if (eventPublisher != null) {
                SecurityEvent event = new SecurityEvent(
                        SecurityEventType.ILLEGAL_ACCESS,
                        request.getRequestURI(),
                        clientIp,
                        ua,
                        "Bot detection score: " + score + ", UA: " + ua,
                        SecurityEvent.Severity.MEDIUM
                );
                eventPublisher.publish(event);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 计算 Bot 评分（0-100）
     *
     * <p>高评分意味着该请求更可能是自动化爬虫。各信号评分可叠加但有上限（100）。
     *
     * @param request HTTP 请求
     * @return Bot 评分（0=正常浏览器，100=确定 Bot）
     */
    private int calculateBotScore(HttpServletRequest request) {
        int score = 0;
        String ua = request.getHeader("User-Agent");

        // 信号 1: User-Agent 检测
        if (ua == null || ua.isBlank()) {
            score += SCORE_MISSING_UA;
        } else if (BOT_UA_PATTERN.matcher(ua).find()) {
            // 包含爬虫关键词，直接判定为 Bot
            return SCORE_BOT_UA;
        } else if (!BROWSER_UA_PATTERN.matcher(ua).find()) {
            score += SCORE_NO_BROWSER_FEATURE;
        }

        // 信号 2: 浏览器特征请求头缺失
        if (request.getHeader("Accept-Language") == null) {
            score += SCORE_NO_ACCEPT_LANGUAGE;
        }
        if (request.getHeader("Accept-Encoding") == null) {
            score += SCORE_NO_ACCEPT_ENCODING;
        }

        // 信号 3: 敏感路径扫描（爬虫常扫描常见管理路径）
        String path = request.getRequestURI();
        if (path != null) {
            for (String sensitive : SENSITIVE_PATHS) {
                if (path.startsWith(sensitive)) {
                    score += 30;
                    break;
                }
            }
        }

        return Math.min(score, 100);
    }

    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }
}
