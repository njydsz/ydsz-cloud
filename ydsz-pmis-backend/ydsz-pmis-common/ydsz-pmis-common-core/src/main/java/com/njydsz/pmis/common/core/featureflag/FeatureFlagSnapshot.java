package com.njydsz.pmis.common.core.featureflag;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特性开关快照
 *
 * <p>反映某个 {@link FeatureFlag} 当前的配置与生效状态，与前端
 * {@code FeatureFlagSnapshot} 类型对齐。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagSnapshot {

    /** 唯一键（对应 {@link FeatureFlag#name()}） */
    private String key;
    /** 分类 */
    private FeatureFlagCategory category;
    /** 描述 */
    private String description;
    /** config 显式值，null = 未配置 */
    private Boolean configuredValue;
    /** 实际生效值 */
    private boolean effectiveValue;
    /** 是否强制开启（SAFETY 永远 true） */
    private boolean mandatory;
    /** 灰度发布比例 0-100，null = 未设置 */
    private Integer rolloutPercentage;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
