package com.njydsz.common.tenant.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.stereotype.Component;

import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.lifecycle.TenantLifecycleManager;
import com.njydsz.common.tenant.metrics.TenantMetrics;

/**
 * 多租户诊断信息贡献者。
 *
 * <p>暴露 {@code /actuator/health/tenant-diagnostics} 端点，
 * 提供完整的运行时诊断信息：
 * <ul>
 *   <li>当前租户上下文快照</li>
 *   <li>Filter 链注册检查</li>
 *   <li>LifecycleManager 存储模式</li>
 *   <li>当前线程 MDC 状态</li>
 *   <li>SQL 拦截指标汇总</li>
 * </ul>
 *
 * <p><b>使用场景：</b>排查租户上下文不生效、SQL 拦截器未触发、状态不同步等问题。
 *
 * <p>通过 {@code management.endpoint.health.tenant-diagnostics.enabled=true} 激活，
 * 默认关闭。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Component
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnProperty(prefix = "management.endpoint.health",
        name = "tenant-diagnostics.enabled", havingValue = "true", matchIfMissing = false)
public class TenantDiagnosticsContributor implements HealthContributor {

    private final TenantProperties properties;
    private final ObjectProvider<TenantMetrics> metricsProvider;

    public TenantDiagnosticsContributor(TenantProperties properties,
                                         ObjectProvider<TenantMetrics> metricsProvider) {
        this.properties = properties;
        this.metricsProvider = metricsProvider;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        Map<String, Object> details = new LinkedHashMap<>();

        // 1. 当前租户上下文快照
        TenantContext currentContext = TenantContextHolder.get();
        if (currentContext != null) {
            Map<String, Object> ctxInfo = new LinkedHashMap<>();
            ctxInfo.put("tenantId", currentContext.getTenantId());
            ctxInfo.put("isSystemTenant", currentContext.isSystemTenant());
            ctxInfo.put("isSuperAdmin", currentContext.isSuperAdmin());
            ctxInfo.put("isSkipIsolation", currentContext.isSkipIsolation());
            ctxInfo.put("fields", currentContext.getFields());
            details.put("currentContext", ctxInfo);
        } else {
            details.put("currentContext", "NOT_SET");
        }

        // 2. 配置快照
        Map<String, Object> configInfo = new LinkedHashMap<>();
        configInfo.put("enabled", properties.isEnabled());
        configInfo.put("mode", properties.getMode());
        configInfo.put("activeFields", properties.getActiveTenantFields().size());
        configInfo.put("lifecycleCheckEnabled", properties.isLifecycleCheckEnabled());
        details.put("config", configInfo);

        // 3. LifecycleManager 模式
        TenantLifecycleManager manager = TenantLifecycleManager.getInstance();
        details.put("lifecycleManager", Map.of(
                "storageMode", manager.isDistributed() ? "redis" : "memory",
                "distributed", manager.isDistributed()
        ));

        // 4. 线程统计
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        long ttlWrapperCount = 0;
        for (ThreadInfo info : threadInfos) {
            if (info.getThreadName().contains("ttl")) {
                ttlWrapperCount++;
            }
        }
        details.put("threadStats", Map.of(
                "totalThreads", threadInfos.length,
                "ttlWrappedThreads", ttlWrapperCount
        ));

        // 5. SQL 指标
        TenantMetrics metrics = metricsProvider.getIfAvailable();
        if (metrics != null) {
            details.put("sqlMetrics", Map.of(
                    "interceptPass", metrics.getInterceptPassCount(),
                    "interceptBlocked", metrics.getInterceptBlockedCount(),
                    "interceptSkipped", metrics.getInterceptSkippedCount(),
                    "failClosed", metrics.getFailClosedCount(),
                    "superAdminBypass", metrics.getSuperAdminCount(),
                    "activeContexts", metrics.getActiveContexts()
            ));
        }

        // 6. 诊断建议
        details.put("diagnostics", buildDiagnostics(manager, currentContext));

        builder.withDetails(details);
        return builder.build();
    }

    private Map<String, String> buildDiagnostics(TenantLifecycleManager manager,
                                                  TenantContext context) {
        Map<String, String> diagnostics = new LinkedHashMap<>();

        // 检查 LifecycleManager 是否为内存存储
        if (!manager.isDistributed()) {
            diagnostics.put("lifecycleStorage",
                    "WARN: LifecycleManager 使用内存存储，多实例部署时状态不同步。"
                            + "建议引入 common-redis 依赖以启用 Redis 共享存储。");
        }

        // 检查上下文状态
        if (context == null) {
            diagnostics.put("context",
                    "INFO: 当前线程无租户上下文。如果在 Web 请求中，"
                            + "请检查 TenantContextWebFilter 是否正确注册。");
        } else if (context.isSkipIsolation()) {
            diagnostics.put("context",
                    "INFO: 当前为跳过隔离模式（匿名 URL），SQL 拦截器不会注入租户条件。");
        }

        // 检查忽略表配置
        Set<String> ignoreTables = properties.getNormalizedIgnoreTables();
        if (ignoreTables.isEmpty()) {
            diagnostics.put("ignoreTables",
                    "INFO: 未配置忽略表，所有表均会注入租户条件。");
        }

        return diagnostics;
    }
}
