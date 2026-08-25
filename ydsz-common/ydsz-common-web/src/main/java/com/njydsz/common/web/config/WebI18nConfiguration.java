package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseI18nConfiguration;

/**
 * Web 端国际化配置（ydsz-web）。
 *
 * <p>提供 ydsz-web 模块的 Locale 解析策略与 {@code LocaleResolver} Bean。
 *
 * <p>优先级：{@code X-Lang} Header > {@code Accept-Language} > Cookie > Session。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
public class WebI18nConfiguration extends BaseI18nConfiguration {

  /**
   * 获取国际化消息文件基础路径
   *
   * @return 基础路径数组
   */
  @Override
  protected String[] getBasenames() {
    return new String[] {"i18n.messages", "i18n/diff-messages", "i18n/file-messages", "i18n/search-messages"};
  }
}
