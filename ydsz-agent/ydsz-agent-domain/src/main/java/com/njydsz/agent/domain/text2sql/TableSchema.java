package com.njydsz.agent.domain.text2sql;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 表 Schema 不可变值对象。
 *
 * <p>携带表名、列定义与描述信息，用于 Schema 召回后注入 LLM Prompt。
 *
 * <p><b>线程安全</b>：全字段 final 且集合经不可变封装，实例不可变，可安全在多线程间共享。
 *
 * @param tableName 表名
 * @param columns 列定义列表（不可变）
 * @param description 表描述（可为 null）
 * @author ydsz-team
 * @since 26.09.01
 */
public record TableSchema(
    String tableName,
    List<ColumnDefinition> columns,
    String description)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 紧凑构造，执行防御性拷贝与必填校验。
   *
   * @throws NullPointerException 当 tableName 或 columns 为 null
   */
  public TableSchema {
    Objects.requireNonNull(tableName, "tableName 不能为 null");
    columns = columns != null ? List.copyOf(columns) : List.of();
  }

  /**
   * 列定义值对象。
   *
   * @param name 列名
   * @param type 数据类型（如 varchar, integer, timestamp）
   * @param description 列描述（可为 null）
   */
  public record ColumnDefinition(String name, String type, String description)
      implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 紧凑构造，校验必填字段。
     *
     * @throws NullPointerException 当 name 或 type 为 null
     */
    public ColumnDefinition {
      Objects.requireNonNull(name, "name 不能为 null");
      Objects.requireNonNull(type, "type 不能为 null");
    }
  }
}
