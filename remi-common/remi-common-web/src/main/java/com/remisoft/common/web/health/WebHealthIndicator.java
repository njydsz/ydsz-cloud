package com.remisoft.common.web.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.web.config.WebCorsProperties;
import com.remisoft.common.web.config.WebTraceProperties;

import nl.basjes.parse.useragent.UserAgentAnalyzer;

/**
 * Web 模块健康指标
 *
 * <p>报告 Web 基座核心能力状态，包括：
 * <ul>
 *   <li>CORS 跨域配置状态</li>
 *   <li>Trace 追踪配置状态</li>
 *   <li>Session 策略（Redis / None）</li>
 *   <li>User-Agent 解析器状态</li>
 *   <li>安全过滤器链状态</li>
 * </ul>
 *
 * @author remi-team
 * @see HealthIndicator
 * @since 1.0.0
 */
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnProperty(prefix = "remi.web.health-indicator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebHealthIndicator implements HealthIndicator {

    private static final int USER_AGENT_CACHE_SIZE = 10000;

    private final WebCorsProperties corsProperties;
    private final WebTraceProperties traceProperties;
    private final ObjectProvider<UserAgentAnalyzer> userAgentAnalyzerProvider;
    private final boolean sessionRedisEnabled;
    private final boolean securityEnabled;

    public WebHealthIndicator(WebCorsProperties corsProperties,
                              WebTraceProperties traceProperties,
                              ObjectProvider<UserAgentAnalyzer> userAgentAnalyzerProvider,
                              boolean sessionRedisEnabled,
                              boolean securityEnabled) {
        this.corsProperties = corsProperties;
        this.traceProperties = traceProperties;
        this.userAgentAnalyzerProvider = userAgentAnalyzerProvider;
        this.sessionRedisEnabled = sessionRedisEnabled;
        this.securityEnabled = securityEnabled;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("corsEnabled", corsProperties.isEnabled());
        details.put("corsAllowCredentials", corsProperties.isAllowCredentials());
        details.put("corsOriginCount",
                corsProperties.getAllowedOriginPatterns() != null
                        ? corsProperties.getAllowedOriginPatterns().size() : 0);

        details.put("traceEnabled", traceProperties.isEnabled());
        details.put("traceResponseHeaderEnabled", traceProperties.isResponseHeaderEnabled());
        details.put("traceRequestLogEnabled", traceProperties.isRequestLogEnabled());
        details.put("traceSamplingRate", traceProperties.getSamplingRate());

        details.put("sessionStrategy", sessionRedisEnabled ? "redis" : "none");
        details.put("securityEnabled", securityEnabled);

        UserAgentAnalyzer analyzer = userAgentAnalyzerProvider.getIfAvailable();
        details.put("userAgentAnalyzerEnabled", analyzer != null);
        if (analyzer != null) {
            details.put("userAgentCacheSize", USER_AGENT_CACHE_SIZE);
        }

        return Health.up().withDetails(details).build();
    }
}
