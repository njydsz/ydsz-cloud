package com.njydsz.common.exception.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化配置属性
 *
 * <p>配置前缀：{@code ydsz.i18n}
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   i18n:
 *     basename: "classpath:i18n/messages"
 *     encoding: "UTF-8"
 *     dev-cache-seconds: 0
 *     prod-cache-seconds: 3600
 *     fallback-to-system-locale: false
 *     supported-locales:
 *       - zh_CN
 *       - en_US
 *     lang-param-name: "lang"
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.i18n")
public class I18nProperties {

  /**
   * 资源文件基路径
   *
   * <p>支持 classpath:、file: 等 Spring Resource 协议。 默认值：{@code classpath:i18n/messages}
   */
  private String basename = "classpath:i18n/messages";

  /**
   * 资源文件编码
   *
   * <p>推荐使用 UTF-8。默认 {@code UTF-8}。
   */
  private String encoding = "UTF-8";

  /**
   * 开发环境缓存刷新间隔（秒）
   *
   * <p>默认 5 秒短缓存：修改 messages.properties 后数秒内生效， 同时避免压测场景下高频 IO 导致性能劣化。 仅在 dev/test profile 下生效。
   *
   * <p>如需立即生效（单次调试），可临时设为 0。
   */
  private int devCacheSeconds = 5;

  /**
   * 生产环境缓存刷新间隔（秒）
   *
   * <p>生产环境建议设置较大值（如 3600）以提升性能。 默认 3600 秒。
   */
  private int prodCacheSeconds = 3600;

  /**
   * 是否回退到系统语言
   *
   * <p>当请求语言不在 supportedLocales 中时，是否回退到系统默认 Locale。 默认 false，固定使用 {@link #getDefaultMessage()}。
   */
  private boolean fallbackToSystemLocale = false;

  /**
   * 找不到国际化消息时的默认提示
   *
   * <p>占位符 {0} 会被替换为实际的消息键。
   */
  private String defaultMessage = "未找到对应的提示信息: {0}";

  /**
   * 支持的语言列表
   *
   * <p>用于解析 Accept-Language 请求头和验证 lang 参数。
   */
  private String[] supportedLocales = {"zh_CN", "en_US", "zh_TW"};

  /**
   * 自定义语言参数名称
   *
   * <p>用于覆盖从请求参数中解析语言的字段名，默认 {@code lang}。
   */
  private String langParamName = "lang";

  /**
   * 获取支持的 Locale 列表（返回副本，防止外部修改内部配置）。
   *
   * @return 支持的 Locale 标签数组（如 zh_CN / en_US）
   */
  public String[] getSupportedLocales() {
    return supportedLocales.clone();
  }

  public void setSupportedLocales(String[] supportedLocales) {
    this.supportedLocales = supportedLocales;
  }
}
