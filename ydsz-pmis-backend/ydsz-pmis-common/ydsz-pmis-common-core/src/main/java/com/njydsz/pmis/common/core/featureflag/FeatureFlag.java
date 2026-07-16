package com.njydsz.pmis.common.core.featureflag;

/**
 * 特性开关枚举
 *
 * <p>定义项目内所有可被 feature flag 控制的特性点。每个枚举值携带分类、描述、
 * 以及是否为强制开启（mandatory）。SAFETY 分类的开关默认 mandatory=true，
 * 永远生效，不可关闭。
 *
 * <p>新增特性点时只需在此枚举追加常量，无需改动其它代码。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FeatureFlag {

    /** 新版仪表盘 */
    NEW_DASHBOARD(FeatureFlagCategory.UI, "新版仪表盘"),
    /** 批量导出 */
    BATCH_EXPORT(FeatureFlagCategory.BUSINESS, "批量导出"),
    /** 增强审计链路 */
    ENHANCED_AUDIT(FeatureFlagCategory.INFRASTRUCTURE, "增强审计链路"),
    /** 风控引擎 V2 */
    RISK_ENGINE_V2(FeatureFlagCategory.BUSINESS, "风控引擎 V2"),
    /** 敏感数据脱敏（安全类，强制开启） */
    SENSITIVE_DATA_MASK(FeatureFlagCategory.SAFETY, "敏感数据脱敏", true);

    /** 分类 */
    private final FeatureFlagCategory category;
    /** 描述 */
    private final String description;
    /** 是否强制开启（不可关闭） */
    private final boolean mandatory;

    FeatureFlag(FeatureFlagCategory category, String description) {
        this(category, description, false);
    }

    FeatureFlag(FeatureFlagCategory category, String description, boolean mandatory) {
        this.category = category;
        this.description = description;
        this.mandatory = mandatory;
    }

    /**
     * 获取分类
     *
     * @return 分类
     */
    public FeatureFlagCategory getCategory() {
        return category;
    }

    /**
     * 获取描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否强制开启
     *
     * @return true 表示强制开启，setEnabled(false) 会被忽略
     */
    public boolean isMandatory() {
        return mandatory;
    }
}
