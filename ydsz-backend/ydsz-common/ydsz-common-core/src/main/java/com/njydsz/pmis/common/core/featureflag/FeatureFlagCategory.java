package com.njydsz.common.core.featureflag;

/**
 * 特性开关分类
 *
 * <p>与前端 {@code FeatureFlagCategory} 类型对齐。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum FeatureFlagCategory {

    /** 基础设施 */
    INFRASTRUCTURE,
    /** 业务 */
    BUSINESS,
    /** UI */
    UI,
    /** 安全（强制开启） */
    SAFETY
}
