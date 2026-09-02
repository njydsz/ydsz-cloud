package com.njydsz.common.jdbc.datasource.hint;

/**
 * Hint 类型枚举，定义强制路由的目标数据源类型。
 *
 * <p>用于 {@link Hint} 中标识路由策略：
 *
 * <ul>
 *   <li>{@link #MASTER} - 强制走主库
 *   <li>{@link #SLAVE} - 强制走从库（由负载均衡策略选择）
 *   <li>{@link #CUSTOM} - 强制走指定的命名数据源
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum HintType {

  /** 强制走主库 */
  MASTER,

  /** 强制走从库（由负载均衡策略选择） */
  SLAVE,

  /** 强制走指定的命名数据源 */
  CUSTOM
}
