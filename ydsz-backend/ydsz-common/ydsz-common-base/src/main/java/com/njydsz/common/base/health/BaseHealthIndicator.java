package com.njydsz.common.base.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.base.config.BaseSecurityHeadersProperties;
import com.njydsz.common.base.config.DocProperties;

/**
 * Base 模块健康指标
 *
 * <p>报告 HTTP 基座模块的核心配置和运行状态，包括：
 * <ul>
 *   <li>时区配置是否生效</li>
 *   <li>安全响应头是否启用</li>
 *   <li>链路追踪是否启用（通过配置间接判断）</li>
 *   <li>文档功能状态</li>
 *   <li>CORS 配置安全性（通过基类属性判断）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class BaseHealthIndicator implements HealthIndicator {

    private final BaseSecurityHeadersProperties securityHeadersProperties;
    private final DocProperties docProperties;

    /**
     * 构造 Base 模块健康指标
     *
     * @param securityHeadersProperties 安全响应头配置
     * @param docProperties 文档配置
     */
    public BaseHealthIndicator(BaseSecurityHeadersProperties securityHeadersProperties,
                               DocProperties docProperties) {
        this.securityHeadersProperties = securityHeadersProperties;
        this.docProperties = docProperties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 时区状态
        String currentTimezone = TimeZone.getDefault().getID();
        details.put("timezone", currentTimezone);
        details.put("timezone.expected", "Asia/Shanghai");

        // 安全响应头状态
        details.put("securityHeaders.enabled", securityHeadersProperties.isEnabled());
        details.put("securityHeaders.frameOptions", securityHeadersProperties.getFrameOptions());
        details.put("securityHeaders.csp", securityHeadersProperties.getCsp() != null
                ? "configured" : "not-set");

        // 文档功能状态
        details.put("doc.enabled", docProperties.isEnabled());
        if (docProperties.isEnabled()) {
            details.put("doc.productionEnabled", docProperties.isProductionEnabled());
            details.put("doc.basicAuth.enabled", docProperties.getBasicAuth().isEnabled());
            details.put("doc.apiDocsPath", docProperties.getApiDocsPath());
            details.put("doc.knife4jPath", docProperties.getKnife4jPath());
        }

        // 健康判断：安全响应头在启用状态下 frameOptions 不为空
        boolean healthy = true;
        if (securityHeadersProperties.isEnabled()
                && (securityHeadersProperties.getFrameOptions() == null
                || securityHeadersProperties.getFrameOptions().isBlank())) {
            healthy = false;
            details.put("warning", "安全响应头已启用但 frameOptions 为空");
        }

        // 文档启用但生产环境未配置 Basic 认证
        if (docProperties.isEnabled() && docProperties.isProductionEnabled()
                && !docProperties.getBasicAuth().isEnabled()) {
            healthy = false;
            details.put("warning", "生产环境文档已启用但 Basic 认证未开启");
        }

        if (healthy) {
            return Health.up().withDetails(details).build();
        }
        return Health.down().withDetails(details).build();
    }
}
