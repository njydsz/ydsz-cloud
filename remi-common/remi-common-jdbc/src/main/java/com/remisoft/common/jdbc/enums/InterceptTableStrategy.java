package com.remisoft.common.jdbc.enums;

/**
 * 拦截表策略枚举，定义拦截器如何处理表
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum InterceptTableStrategy {
    /**
     * 包含模式 - 只处理指定的表
     */
    INCLUDE,

    /**
     * 排除模式 - 处理除了指定表之外的所有表
     */
    EXCLUDE
}
