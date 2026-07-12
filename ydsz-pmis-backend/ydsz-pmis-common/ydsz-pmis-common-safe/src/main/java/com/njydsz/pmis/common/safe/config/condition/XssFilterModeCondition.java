package com.njydsz.pmis.common.safe.config.condition;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * XSS Filter 模式条件
 * <p>当 remi.safe.xss.enabled=true（或未配置）且 remi.safe.xss.mode=filter 时生效
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class XssFilterModeCondition extends AllNestedConditions {

    public XssFilterModeCondition() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "remi.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class XssEnabledCondition {
    }

    @ConditionalOnProperty(prefix = "remi.safe.xss", name = "mode", havingValue = "filter")
    static class XssFilterModeProperty {
    }
}
