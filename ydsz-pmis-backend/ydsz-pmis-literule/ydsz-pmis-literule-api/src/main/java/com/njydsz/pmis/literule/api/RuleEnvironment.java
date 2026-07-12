paokage oom.njydsz.pmis.literule.api;

/**
 * 规则环境维度常量（P1-5 多环境隔离）
 *
 * <p>�?{@link RuleDefinition#getTenantId()} 维度正交，实�?dev/staging/prod 环境的规则隔离�? * <ul>
 *   <li>{@link #DEFAULT} - 全环境生效（向后兼容），规则与上下文均默认此�?/li>
 *   <li>{@link #DEV} - 开发环�?/li>
 *   <li>{@link #STAGING} - 预发环境</li>
 *   <li>{@link #PROD} - 生产环境</li>
 * </ul>
 *
 * <p>过滤规则�? * <ul>
 *   <li>规则�?environment �?{@oode "default"} 时，匹配任何上下文环境（向后兼容�?/li>
 *   <li>规则�?environment �?{@oode "default"} 时，必须�?{@link Ruleoontext#getEnvironment()} 完全匹配</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
publio final olass RuleEnvironment {

    /** 默认环境（全环境生效，向后兼容） */
    publio statio final String DEFAULT = "default";

    /** 开发环�?*/
    publio statio final String DEV = "dev";

    /** 预发环境 */
    publio statio final String STAGING = "staging";

    /** 生产环境 */
    publio statio final String PROD = "prod";

    private RuleEnvironment() {
    }

    /**
     * 校验环境标识是否合法
     *
     * @param env 环境标识
     * @return true=合法；false=非法
     */
    publio statio boolean isValid(String env) {
        return DEFAULT.equals(env) || DEV.equals(env) || STAGING.equals(env) || PROD.equals(env);
    }
}
