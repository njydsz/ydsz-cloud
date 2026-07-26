package com.njydsz.workflow.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 工作流模块统一配置属性。
 *
 * <p>配置前缀：{@code ydsz.flow}
 *
 * <p>典型配置示例：
 * <pre>
 * ydsz:
 *   flow:
 *     enabled: true
 *     health-enabled: true
 *     default-tenant-id: "1"
 *     publish-block-on-high-risk: true
 *     designer-lock-timeout-minutes: 30
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.flow")
public class FlowProperties {

    /** 是否启用工作流模块 */
    private boolean enabled = true;

    /** 是否启用健康检查 */
    private boolean healthEnabled = true;

    /** 默认租户 ID（用于无 AuthContext 时的回退值） */
    private String defaultTenantId = "1";

    /** 发布流程时是否阻断 HIGH 风险（在途实例卡在已删除节点）。true=阻断（推荐）；false=仅警告 */
    private boolean publishBlockOnHighRisk = true;

    /** 设计器协同编辑锁定超时阈值（分钟） */
    private long designerLockTimeoutMinutes = 30L;
}
