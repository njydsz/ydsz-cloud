package com.remisoft.literule.api;

/**
 * 规则环境维度常量（P1-5 多环境隔离）
 *
 * <p>与 {@link RuleDefinition#getTenantId()} 维度正交，实现 dev/staging/prod 环境的规则隔离。
 * <ul>
 *   <li>{@link #DEFAULT} - 全环境生效（向后兼容），规则与上下文均默认此值</li>
 *   <li>{@link #DEV} - 开发环境</li>
 *   <li>{@link #STAGING} - 预发环境</li>
 *   <li>{@link #PROD} - 生产环境</li>
 * </ul>
 *
 * <p>过滤规则：
 * <ul>
 *   <li>规则的 environment 为 {@code "default"} 时，匹配任何上下文环境（向后兼容）</li>
 *   <li>规则的 environment 非 {@code "default"} 时，必须与 {@link RuleContext#getEnvironment()} 完全匹配</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class RuleEnvironment {

    /** 默认环境（全环境生效，向后兼容） */
    public static final String DEFAULT = "default";

    /** 开发环境 */
    public static final String DEV = "dev";

    /** 预发环境 */
    public static final String STAGING = "staging";

    /** 生产环境 */
    public static final String PROD = "prod";

    private RuleEnvironment() {
    }

    /**
     * 校验环境标识是否合法
     *
     * @param env 环境标识
     * @return true=合法；false=非法
     */
    public static boolean isValid(String env) {
        return DEFAULT.equals(env) || DEV.equals(env) || STAGING.equals(env) || PROD.equals(env);
    }
}
