package com.njydsz.pmis.common.featureflag;

/**
 * 特性开关枚举 (批次 20 P2-3)
 *
 * <p>PMIS 系统所有可灰度发布 / 紧急回滚的特性统一在此声明,
 * 业务代码通过 {@link FeatureFlagService#isEnabled(FeatureFlag, Long)} 判断是否启用.
 *
 * <p>每个 flag 对应 config 分组 {@code feature_flag} 下的一个键 (大写).
 * 当 config 中心未配置时, {@link #isEnabledByDefault()} 决定回退行为.
 *
 * <h3>分类</h3>
 * <ul>
 *   <li>INFRASTRUCTURE - 基础设施类 (Sentry 监控 / 新版报表)</li>
 *   <li>BUSINESS - 业务能力 (高级利润模拟 / AI Agent 编排)</li>
 *   <li>UI - 界面特性 (驾驶舱 v2 / 高管看板)</li>
 *   <li>SAFETY - 安全合规 (审计日志强制 / 二次认证)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
public enum FeatureFlag {

    // =============== 基础设施类 ===============
    /** Sentry 错误监控 (前后端 SDK) */
    SENTRY_MONITORING("INFRASTRUCTURE", "Sentry 错误监控"),

    /** OpenTelemetry 分布式追踪 */
    DISTRIBUTED_TRACING("INFRASTRUCTURE", "OpenTelemetry 链路追踪"),

    /** Prometheus + Grafana 监控指标 */
    PROMETHEUS_METRICS("INFRASTRUCTURE", "Prometheus 指标采集"),

    /** 灰度发布 / 蓝绿部署 */
    CANARY_DEPLOY("INFRASTRUCTURE", "金丝雀灰度发布"),

    // =============== 业务类 ===============
    /** AI 多智能体编排 (AgentScope) */
    AGENT_ORCHESTRATION("BUSINESS", "AI 多智能体编排"),

    /** 高级利润模拟 */
    ADVANCED_PROFIT_SIMULATION("BUSINESS", "高级利润模拟"),

    /** 风险预警引擎 */
    RISK_PREDICTION_ENGINE("BUSINESS", "风险预警引擎"),

    /** 资源调度推荐 (AI) */
    AI_RESOURCE_RECOMMEND("BUSINESS", "AI 资源调度推荐"),

    /** 客户信用评分 (新增客户 30 分基线) */
    CUSTOMER_CREDIT_SCORING("BUSINESS", "客户信用评分"),

    /** 双费率利润对比 */
    DUAL_RATE_PROFIT("BUSINESS", "双费率利润对比"),

    // =============== 界面类 ===============
    /** 经营驾驶舱 v2 (批次18 增强版) */
    COCKPIT_V2("UI", "经营驾驶舱 v2"),

    /** 高管看板 (8 KPI + 健康度评分) */
    EXECUTIVE_DASHBOARD("UI", "高管看板"),

    /** 国际化 i18n (中英双语) */
    I18N_LOCALIZATION("UI", "国际化 i18n"),

    /** 暗黑模式 */
    DARK_MODE("UI", "暗黑模式"),

    // =============== 安全合规类 ===============
    /** 审计日志强制开启 (不允许关闭) */
    AUDIT_LOG_MANDATORY("SAFETY", "审计日志强制开启"),

    /** 敏感操作二次认证 */
    SENSITIVE_REAUTH("SAFETY", "敏感操作二次认证"),

    /** 数据导出审计 */
    DATA_EXPORT_AUDIT("SAFETY", "数据导出审计"),

    /** TOTP 双因素认证 */
    TOTP_TWO_FACTOR("SAFETY", "TOTP 双因素认证");

    private final String category;
    private final String description;

    FeatureFlag(String category, String description) {
        this.category = category;
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 默认开关值. SAFETY 类默认 true, 其它默认 false.
     * SAFETY 类不允许通过 config 关闭, 防止误操作.
     */
    public boolean isEnabledByDefault() {
        return "SAFETY".equals(category);
    }

    /**
     * 是否强制开启 (即 config 中的开关值无效, 永远 true)
     */
    public boolean isMandatory() {
        return "SAFETY".equals(category);
    }

    /**
     * 转 config key (大写下划线)
     */
    public String configKey() {
        return name();
    }
}
