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
  /** 智能合并（追加新内容到已有文件）。 */
  MERGE("MERGE", "合并");

  /** 策略码。 */
  private final String code;
  /** 策略描述。 */
  private final String description;
}
