package com.njydsz.common.web.health;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.web.config.WebCorsProperties;
import com.njydsz.common.web.config.WebTraceProperties;

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
 *   <li>JVM 堆内存使用概况</li>
 * </ul>
 *
 * <p><b>运行时检查：</b>User-Agent 解析器可用性通过实际解析样例 UA 字符串验证，
 * 确保懒加载 Bean 在被健康检查触发时能正确初始化。JVM 堆内存详情提供内存压力观测信号。
 *
 * @author ydsz-team
 * @see HealthIndicator
 * @since 1.0.0
 */
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnProperty(prefix = "ydsz.web.health-indicator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebHealthIndicator implements HealthIndicator {

    private static final int USER_AGENT_CACHE_SIZE = 10000;

    private static final String SAMPLE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 字节到 MB 的换算因子
     */
    private static final int BYTES_PER_MB = 1024 * 1024;

    /**
     * 内存使用率百分比换算因子
     */
    private static final int PERCENTAGE_FACTOR = 100;

    /**
     * 百分比计算精度因子（保留两位小数）
     */
    private static final double PERCENTAGE_PRECISION = 100.0;

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

        // User-Agent 解析器实际可用性检查（仅验证是否可初始化并正确解析）
        checkUserAgentAnalyzer(details);

        // JVM 堆内存使用概况
        collectHeapMemoryDetails(details);

        return Health.up().withDetails(details).build();
    }

    /**
     * 检查 User-Agent 解析器可用性
     *
     * @param details 健康详情映射
     */
    private void checkUserAgentAnalyzer(Map<String, Object> details) {
        UserAgentAnalyzer analyzer = userAgentAnalyzerProvider.getIfAvailable();
        if (analyzer != null) {
            details.put("userAgentAnalyzerEnabled", true);
            details.put("userAgentCacheSize", USER_AGENT_CACHE_SIZE);
            try {
                analyzer.parse(SAMPLE_USER_AGENT);
                details.put("userAgentParserWorking", true);
            } catch (Exception e) {
                details.put("userAgentParserWorking", false);
                details.put("userAgentParserError", e.getMessage());
            }
        } else {
            details.put("userAgentAnalyzerEnabled", false);
        }
    }

    /**
     * 采集 JVM 堆内存使用概况
     *
     * @param details 健康详情映射
     */
    private void collectHeapMemoryDetails(Map<String, Object> details) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        Map<String, Object> memoryDetails = new LinkedHashMap<>();
        memoryDetails.put("usedMB", heapUsage.getUsed() / BYTES_PER_MB);
        memoryDetails.put("committedMB", heapUsage.getCommitted() / BYTES_PER_MB);
        memoryDetails.put("maxMB", heapUsage.getMax() / BYTES_PER_MB);
        double usagePercent = heapUsage.getMax() > 0
                ? Math.round((double) heapUsage.getUsed() / heapUsage.getMax()
                        * PERCENTAGE_FACTOR * PERCENTAGE_PRECISION) / PERCENTAGE_PRECISION
                : 0.0;
        memoryDetails.put("usagePercent", usagePercent);
        details.put("heapMemory", memoryDetails);
    }
}
