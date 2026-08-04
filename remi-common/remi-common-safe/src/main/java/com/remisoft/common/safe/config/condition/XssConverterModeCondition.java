package com.remisoft.common.safe.config.condition;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * XSS Converter 模式条件
 * <p>当 remi.safe.xss.enabled=true（或未配置）且 remi.safe.xss.mode=converter（或未配置）时生效
 *
 * @author remi-team
 * @since 1.0.0
 */
public class XssConverterModeCondition extends AllNestedConditions {

    public XssConverterModeCondition() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    /**
     * XSS 功能启用条件：{@code remi.safe.xss.enabled=true}，未配置时默认生效。
     */
    @ConditionalOnProperty(prefix = "remi.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class XssEnabledCondition {
    }

    /**
     * XSS 转换器模式条件：{@code remi.safe.xss.mode=converter}，未配置时默认生效。
     */
    @ConditionalOnProperty(prefix = "remi.safe.xss", name = "mode", havingValue = "converter", matchIfMissing = true)
    static class XssConverterModeProperty {
    }
}
