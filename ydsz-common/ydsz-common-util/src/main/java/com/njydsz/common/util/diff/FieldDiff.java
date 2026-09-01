package com.njydsz.common.util.diff;

import java.io.Serializable;

import lombok.Data;

import com.njydsz.common.util.api.Experimental;
import com.njydsz.common.util.message.MessageUtils;

/**
 * 字段差异记录
 *
 * <p>记录单个字段在更新操作前后的值变化，用于生成操作日志的变更详情。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Experimental("能力储备：字段级差异对比（审计日志场景），当前平台内暂无消费方，启用前请确认测试覆盖")
@Data
public class FieldDiff implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 字段 Java 名称 */
  private final String fieldName;

  /** 字段中文名称（展示用） */
  private final String fieldLabel;

  /** 变更前值（已脱敏、已格式化） */
  private final String oldValue;

  /** 变更后值（已脱敏、已格式化） */
  private final String newValue;

  /** 是否为敏感字段 */
  private final boolean sensitive;

  /**
   * 创建字段差异记录
   *
   * @param fieldName 字段 Java 名称
   * @param fieldLabel 字段中文名称
   * @param oldValue 变更前值
   * @param newValue 变更后值
   * @param sensitive 是否为敏感字段
   * @return 字段差异记录
   */
  public static FieldDiff of(
      String fieldName, String fieldLabel, String oldValue, String newValue, boolean sensitive) {
    return new FieldDiff(fieldName, fieldLabel, oldValue, newValue, sensitive);
  }

  /** 空值占位符 i18n key */
  private static final String KEY_EMPTY_PLACEHOLDER = "diff.empty.placeholder";

  /** 变更分隔符 i18n key */
  private static final String KEY_CHANGE_SEPARATOR = "diff.change.separator";

  /**
   * 生成可读的差异描述
   *
   * <p>支持多语言：空值占位符和分隔符通过 {@link MessageUtils} 按当前 Locale 解析。 未接入 Spring MessageSource 时使用中文默认值。
   *
   * @return 格式如 "用户名: 张三 → 李四"
   */
  public String toReadableString() {
    String emptyPlaceholder = MessageUtils.getMessage(KEY_EMPTY_PLACEHOLDER, "(空)");
    String separator = MessageUtils.getMessage(KEY_CHANGE_SEPARATOR, " → ");
    return fieldLabel
        + ": "
        + (oldValue == null ? emptyPlaceholder : oldValue)
        + separator
        + (newValue == null ? emptyPlaceholder : newValue);
  }
}
