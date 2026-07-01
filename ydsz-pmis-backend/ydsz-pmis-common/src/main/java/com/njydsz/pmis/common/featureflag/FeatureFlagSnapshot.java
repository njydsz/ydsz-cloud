package com.njydsz.pmis.common.featureflag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 特性开关快照 (批次 20 P2-3)
 *
 * <p>某一个 flag 在当前时刻的实际生效状态. 通过 {@link FeatureFlagService#snapshot()}
 * 获取全量快照, 用于 admin 控制台展示 / 灰度发布面板.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 唯一键 (对应 FeatureFlag.name()) */
    private String key;

    /** 分类 (INFRASTRUCTURE / BUSINESS / UI / SAFETY) */
    private String category;

    /** 描述 */
    private String description;

    /** config 中显式配置的值, null 表示未配置 */
    private Boolean configuredValue;

    /** 实际生效值 = configuredValue OR default */
    private boolean effectiveValue;

    /** 是否强制开启 (SAFETY 类永远 true) */
    private boolean mandatory;

    /** 灰度发布比例 0-100, null = 全量 / 全无 */
    private Integer rolloutPercentage;

    /** 上次更新时间 */
    private Instant updatedAt;
}
