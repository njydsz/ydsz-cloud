package com.njydsz.pmis.common.core.featureflag;

/**
 * 特性开关枚举。
 *
 * <p>定义系统中所有可用的特性开关。新增开关时在此枚举中添加即可。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FeatureFlag {
    /** 新工作流引擎 */
    NEW_WORKFLOW_ENGINE("工作流引擎", "engine"),
    /** 多租户配额 */
    MULTI_TENANT_QUOTA("多租户配额", "tenant"),
    /** 自适应调度 */
    ADAPTIVE_SCHEDULING("自适应调度", "scheduler"),
    /** 跨集群调度 */
    CROSS_CLUSTER_DISPATCH("跨集群调度", "scheduler"),
    /** 灰度发布 */
    CANARY_RELEASE("灰度发布", "release"),
    /** 新导出引擎 */
    NEW_EXPORT_ENGINE("导出引擎", "export"),
    /** 操作日志补偿 */
    AUDIT_FALLBACK("审计补偿", "audit"),
    /** 沙箱脚本执行 */
    SANDBOX_SCRIPT("沙箱脚本", "security");

    private final String description;
    private final String category;

    FeatureFlag(String description, String category) {
        this.description = description;
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }
}
