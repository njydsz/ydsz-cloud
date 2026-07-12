package com.njydsz.pmis.common.safe.config.condition;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * XSS Converter 模式条件
 * <p>当 ydsz.safe.xss.enabled=true（或未配置）且 ydsz.safe.xss.mode=converter（或未配置）时生效
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class XssConverterModeCondition extends AllNestedConditions {

    public XssConverterModeCondition() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "ydsz.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class XssEnabledCondition {
    }

    @ConditionalOnProperty(prefix = "ydsz.safe.xss", name = "mode", havingValue = "converter", matchIfMissing = true)
    static class XssConverterModeProperty {
    }
}
