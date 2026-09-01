package com.njydsz.common.safe.config.condition;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * XSS Filter 模式条件
 *
 * <p>当 ydsz.safe.xss.enabled=true（或未配置）且 ydsz.safe.xss.mode=filter 时生效
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class XssFilterModeCondition extends AllNestedConditions {

  public XssFilterModeCondition() {
    super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
  }

  /** XSS 功能启用条件：{@code ydsz.safe.xss.enabled=true}，未配置时默认生效。 */
  @ConditionalOnProperty(
      prefix = "ydsz.safe.xss",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class XssEnabledCondition {}

  /** XSS 过滤器模式条件：{@code ydsz.safe.xss.mode=filter}，未配置时默认生效。 */
  @ConditionalOnProperty(prefix = "ydsz.safe.xss", name = "mode", havingValue = "filter")
  static class XssFilterModeProperty {}
}
