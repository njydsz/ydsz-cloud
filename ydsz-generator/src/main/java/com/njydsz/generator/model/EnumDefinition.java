package com.njydsz.generator.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 枚举定义元数据。
 *
 * <p>从数据库字段注释中解析枚举值信息，用于生成 domain/enums 包下的枚举类。
 *
 * <p><b>注释格式约定：</b>字段注释中使用 `{@code 枚举名:值=标签;值=标签}` 格式，
 * 如 `{@code 状态:1=启用;0=禁用}` 会解析为名为 {@code 状态} 的枚举定义，包含两个枚举项。
 *
 * @author ydsz-team
 * @since 26.09.04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnumDefinition {

  /** 枚举类名（PascalCase，如 {@code TenantStatusEnum}） */
  private String enumClassName;

  /** 字段名（用于关联数据库列） */
  private String fieldName;

  /** 字段注释中的原始描述（冒号前的部分） */
  private String description;

  /** 枚举项列表 */
  private List<EnumItem> items;

  /** 字段 Java 类型（枚举值类型，通常为 Integer 或 String） */
  private String valueType;

  /**
   * 单个枚举项。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EnumItem {
    /** 枚举常量名（大写，如 {@code ENABLED}） */
    private String code;
    /** 枚举值（数值或字符串） */
    private Integer value;
    /** 枚举值（字符串类型时使用） */
    private String strValue;
    /** 中文标签 */
    private String label;
  }
}
