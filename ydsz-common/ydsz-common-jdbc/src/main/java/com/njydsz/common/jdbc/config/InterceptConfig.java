package com.njydsz.common.jdbc.config;

import java.util.HashSet;
import java.util.Set;

import com.njydsz.common.jdbc.enums.InterceptTableStrategy;

/**
 * SQL 拦截器配置类
 *
 * <p>定义 SQL 拦截器的基本配置参数，包括拦截策略、启用状态、目标表列表和字段名。
 *
 * <h2>配置说明</h2>
 *
 * <ul>
 *   <li>interceptTableStrategy：表拦截策略（INCLUDE/EXCLUDE）
 *   <li>enabled：是否启用拦截
 *   <li>tables：需要拦截或排除的表集合
 *   <li>column：目标字段名
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see InterceptTableStrategy
 */
public class InterceptConfig {

  private InterceptTableStrategy interceptTableStrategy = InterceptTableStrategy.EXCLUDE;
  private Boolean isEnabled = true;
  private Set<String> tables = new HashSet<>(16);
  private String column = "";

  public InterceptTableStrategy getInterceptTableStrategy() { return interceptTableStrategy; }
  public void setInterceptTableStrategy(InterceptTableStrategy interceptTableStrategy) { this.interceptTableStrategy = interceptTableStrategy; }

  public Boolean getIsEnabled() { return isEnabled; }
  public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

  public Set<String> getTables() { return tables; }
  public void setTables(Set<String> tables) { this.tables = tables; }

  public String getColumn() { return column; }
  public void setColumn(String column) { this.column = column; }
}
