package com.njydsz.generator.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件冲突策略枚举。
 *
 * <p>当生成代码时目标文件已存在，采用哪种策略处理。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Getter
@AllArgsConstructor
public enum ConflictStrategyEnum {

  /** 跳过已有文件（保留现有代码）。 */
  SKIP("SKIP", "跳过"),
  /** 覆盖已有文件（备份原文件到 history）。 */
  OVERRIDE("OVERRIDE", "覆盖并备份"),
  /**
   * 追加模式（在已有文件末尾追加生成内容）。
   *
   * <p>生成的代码会拼接在原文件末尾并标记 {@code // AUTO-GEN}，
   * 适用于需要在已有文件基础上增量扩展的场景。</p>
   */
  APPEND("APPEND", "追加");

  /** 策略码。 */
  private final String code;
  /** 策略描述。 */
  private final String description;
}
