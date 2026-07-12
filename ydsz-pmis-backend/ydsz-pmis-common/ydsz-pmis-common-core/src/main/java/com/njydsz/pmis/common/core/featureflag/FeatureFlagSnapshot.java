package com.njydsz.pmis.common.core.featureflag;

import lombok.Data;

/**
 * 特性开关快照。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class FeatureFlagSnapshot {

    /** 特性开关名称 */
    private String key;
    /** 描述 */
    private String description;
    /** 分类 */
    private String category;
    /** 是否全局启用 */
    private boolean enabled;
    /** 灰度百分比（0-100） */
    private int rolloutPercentage;
    /** 上次更新时间（毫秒时间戳） */
    private long updatedAt;

    public FeatureFlagSnapshot() {
    }

    public FeatureFlagSnapshot(FeatureFlag flag, boolean enabled, int rolloutPercentage) {
        this.key = flag.name();
        this.description = flag.getDescription();
        this.category = flag.getCategory();
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.updatedAt = System.currentTimeMillis();
    }
}
