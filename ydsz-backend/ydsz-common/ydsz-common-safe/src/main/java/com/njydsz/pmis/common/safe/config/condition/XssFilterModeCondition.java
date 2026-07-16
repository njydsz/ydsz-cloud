package com.njydsz.common.safe.config.condition;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * XSS Filter 模式条件
 * <p>当 ydsz.safe.xss.enabled=true（或未配置）且 ydsz.safe.xss.mode=filter 时生效
 *
 * @since 1.0.0
 * 
 */
public class XssFilterModeCondition extends AllNestedConditions {

    public XssFilterModeCondition() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "ydsz.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class XssEnabledCondition {
    }

    @ConditionalOnProperty(prefix = "ydsz.safe.xss", name = "mode", havingValue = "filter")
    static class XssFilterModeProperty {
    }
}
