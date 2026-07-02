package com.njydsz.pmis.literule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiteRule 配置属性
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.literule")
public class LiteRuleProperties {

    /** 是否启用自动注册内置规则 */
    private boolean autoRegisterBuiltinRules = true;

    /** 是否启用规则热加载（监听 RuleConfigRefreshEvent） */
    private boolean hotReloadEnabled = true;

    /** 是否启用执行统计 */
    private boolean statsEnabled = true;

    /** 是否启用 dry-run 仿真 */
    private boolean dryRunEnabled = true;
}
