package com.njydsz.common.domain.query;

import java.io.Serializable;
import java.util.Locale;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 排序项（结构化，替代字符串拼接）。
 *
 * <p>将「排序列 + 方向」二元组封装为不可变对象，避免 PageQuery 以 {@code List<String>} 存储排序项并在每次读取时重新解析字符串。
 *
 * <pre>{@code
 * OrderItem.of("created_at", true)          // ASC
 * OrderItem.desc("updated_at")              // DESC
 * orderItem.toSql()                         // "created_at ASC"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
public class OrderItem implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 排序列名（已通过 SQL 安全校验） */
  private final String column;

  /** 排序方向 */
  private final Direction direction;

  /** 排序方向 */
  public enum Direction {
/** asc */
    ASC,
/** desc */
    DESC;

    /**
     * 解析方向字符串（大小写不敏感）
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
   * 创建升序排序项
   *
   * @param column 排序列名
   * @return 排序项
   */
  public static OrderItem asc(String column) {
    return new OrderItem(column, Direction.ASC);
  }

  /**
   * 创建降序排序项
   *
   * @param column 排序列名
   * @return 排序项
   */
  public static OrderItem desc(String column) {
    return new OrderItem(column, Direction.DESC);
  }

  /**
   * 创建排序项
   *
   * @param column 排序列名
   * @param isAsc true 升序，false 降序
   * @return 排序项
   */
  public static OrderItem of(String column, boolean isAsc) {
    return new OrderItem(column, isAsc ? Direction.ASC : Direction.DESC);
  }

  /**
   * 生成 SQL ORDER BY 片段（不含 "ORDER BY" 前缀）
   *
   * @return 如 {@code "created_at ASC"}
   */
  public String toSql() {
    return column + " " + direction.name();
  }

  @Override
  public String toString() {
    return toSql();
  }
}
