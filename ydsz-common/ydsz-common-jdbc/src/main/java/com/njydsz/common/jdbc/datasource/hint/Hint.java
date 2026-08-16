package com.njydsz.common.jdbc.datasource.hint;

import java.util.Objects;

/**
 * 数据源路由 Hint 不可变值对象。
 *
 * <p>封装路由类型（{@link HintType}）和可选的自定义数据源名称。 由 {@link HintManager} 创建并存储在 ThreadLocal 中， 供 {@code
 * DynamicRoutingDataSource} 在路由决策时优先使用。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 强制走主库
 * HintManager.masterOnly();
 *
 * // 强制走指定数据源
 * HintManager.datasource("report_slave");
 *
 * // 使用 try-with-resources 自动清理
 * try (HintManager.Scope scope = HintManager.masterOnlyScope()) {
 *     // 此处的查询全部走主库
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class Hint {

  /** 路由类型 */
  private final HintType type;

  /** 自定义数据源名称（仅当 type=CUSTOM 时有效） */
  private final String dsName;

  /**
   * 构造路由 Hint
   *
   * @param type 路由类型，不可为 null
   * @param dsName 自定义数据源名称，type=CUSTOM 时不可为 null
   */
  public Hint(HintType type, String dsName) {
    this.type = Objects.requireNonNull(type, "HintType must not be null");
    this.dsName = dsName;
  }

  /**
   * 构造主库/从库路由 Hint（无需指定数据源名称）
   *
   * @param type 路由类型，不可为 null
   */
  public Hint(HintType type) {
    this(type, null);
  }

  public HintType getType() {
    return type;
  }

  public String getDsName() {
    return dsName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Hint hint)) {
      return false;
    }
    return type == hint.type && Objects.equals(dsName, hint.dsName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, dsName);
  }

  @Override
  public String toString() {
    if (HintType.CUSTOM == type) {
      return "Hint{type=CUSTOM, dsName='" + dsName + "'}";
    }
    return "Hint{type=" + type + "}";
  }
}
