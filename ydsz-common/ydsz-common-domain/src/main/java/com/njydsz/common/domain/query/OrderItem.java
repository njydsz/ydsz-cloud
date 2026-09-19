package com.njydsz.common.domain.query;

import java.io.Serializable;
import java.util.Locale;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 排序项（结构化，替代字符串拼接）。
 *
 * <p>将「排序列 + 方向」二元组封装为不可变对象，避免 PageQuery 以 {@code List<String>} 存储排序项并在每次读取时重新解析字符串。
 *
 * <p>SQL 片段在构造时预计算（{@link #sql}），{@link #toSql()} 直接返回缓存值，
 * 避免 MyBatis-Plus Wrapper 多次读取排序条件时重复字符串拼接。
 *
 * <pre>{@code
 * OrderItem.of("created_at", true)          // ASC
 * OrderItem.desc("updated_at")              // DESC
 * orderItem.toSql()                         // "created_at ASC"
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@EqualsAndHashCode
public class OrderItem implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 排序列名（已通过 SQL 安全校验） */
  private final String column;

  /** 排序方向 */
  private final Direction direction;

  /** 预计算的 SQL 片段（column + " " + direction），避免重复拼接 */
  private final String sql;

  /** 排序方向枚举 */
  public enum Direction {
    /** 升序 */
    ASC,
    /** 降序 */
    DESC;

    /**
     * 解析方向字符串（大小写不敏感）。
     *
     * @param value 方向字符串（asc/desc）
     * @return 方向枚举；无法识别时返回 null
     */
    public static Direction of(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String upper = value.trim().toUpperCase(Locale.ROOT);
      return switch (upper) {
        case "ASC" -> ASC;
        case "DESC" -> DESC;
        default -> null;
      };
    }
  }

  /**
   * 构造排序项（预计算 SQL 片段）。
   *
   * @param column 排序列名
   * @param direction 排序方向
   */
  OrderItem(String column, Direction direction) {
    this.column = column;
    this.direction = direction;
    this.sql = column + " " + direction.name();
  }

  /**
   * 创建升序排序项。
   *
   * @param column 排序列名
   * @return 排序项
   */
  public static OrderItem asc(String column) {
    return new OrderItem(column, Direction.ASC);
  }

  /**
   * 创建降序排序项。
   *
   * @param column 排序列名
   * @return 排序项
   */
  public static OrderItem desc(String column) {
    return new OrderItem(column, Direction.DESC);
  }

  /**
   * 创建排序项。
   *
   * @param column 排序列名
   * @param isAsc true 升序，false 降序
   * @return 排序项
   */
  public static OrderItem of(String column, boolean isAsc) {
    return new OrderItem(column, isAsc ? Direction.ASC : Direction.DESC);
  }

  /**
   * 获取 SQL ORDER BY 片段（不含 "ORDER BY" 前缀）。
   *
   * <p>返回构造时预计算的缓存值，避免重复字符串拼接。
   *
   * @return 如 {@code "created_at ASC"}
   */
  public String toSql() {
    return sql;
  }

  @Override
  public String toString() {
    return sql;
  }
}
