package com.njydsz.common.base.config;

/**
 * 平台运行模式枚举。
 *
 * <p>用于区分当前应用运行的端类型，实现 Web/App 模块的自动隔离。
 *
 * <p>配置方式：
 *
 * <ul>
 *   <li>显式配置：{@code ydsz.platform.mode=web} 或 {@code ydsz.platform.mode=app}
 *   <li>自动探测：根据 classpath 中是否存在 ydsz-common-web 或 ydsz-common-app 判断
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum PlatformMode {

  /** PC Web 端（浏览器访问） */
  WEB,

  /** 移动端 App 端（iOS/Android/小程序） */
  APP;

  /** 默认平台模式。 */
  public static final PlatformMode DEFAULT = WEB;
}
