package com.njydsz.pmis.common.jdbc.enums;

/**
 * 拦截表策略枚举，定义拦截器如何处理表
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
