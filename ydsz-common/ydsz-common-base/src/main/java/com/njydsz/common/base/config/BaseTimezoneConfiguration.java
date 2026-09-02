package com.njydsz.common.base.config;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

/**
 * 时区配置基类（Web/App 共享）
 *
 * <p>通过 {@link PostConstruct} 在 Bean 初始化时强制将 JVM 默认时区设置为 配置值（默认 {@code
 * Asia/Shanghai}，UTC+8），保证全局时间一致性。
 *
 * <p>可通过 {@code ydsz.base.timezone} 配置项自定义时区，例如：
 *
 * <pre>{@code
 * ydsz:
 *   base:
 *     timezone: America/New_York
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public abstract class BaseTimezoneConfiguration {

  /** 默认时区（Asia/Shanghai，UTC+8） */
  protected static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

  /** 时区配置值，默认 Asia/Shanghai */
  @Value("${ydsz.base.timezone:" + DEFAULT_TIMEZONE + "}")
  private String timezone;

  /**
   * 初始化时区
   *
   * <p>在 Bean 初始化完成后立即执行，确保应用启动后所有线程 使用的都是配置指定的时区。
   */
  @PostConstruct
  public void defaultTimeZone() {
    String tz = timezone != null && !timezone.isBlank() ? timezone : DEFAULT_TIMEZONE;
    TimeZone.setDefault(TimeZone.getTimeZone(tz));
  }

  /**
   * 获取配置的时区 ID
   *
   * @return 时区 ID（如 {@code Asia/Shanghai}），未配置时返回默认值
   */
  public String getTimezone() {
    return timezone != null && !timezone.isBlank() ? timezone : DEFAULT_TIMEZONE;
  }
}
