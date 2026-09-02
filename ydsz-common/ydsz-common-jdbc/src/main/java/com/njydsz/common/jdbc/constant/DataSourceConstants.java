package com.njydsz.common.jdbc.constant;

/**
 * 数据源常量定义。
 *
 * <p>用于 {@code @DS} 注解指定动态数据源名称。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DataSourceConstants {

  private DataSourceConstants() {}

  /** 主库 */
  public static final String MASTER = "master";

  /** 从库 */
  public static final String SLAVE = "slave";

  /** 默认数据源 */
  public static final String DEFAULT = "default";
}
