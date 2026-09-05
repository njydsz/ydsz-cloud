package com.njydsz.generator.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代码生成任务状态枚举。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Getter
@AllArgsConstructor
public enum GenStatusEnum {

  /** 执行中。 */
  RUNNING("RUNNING", "执行中"),
  /** 全部生成成功。 */
  SUCCESS("SUCCESS", "生成成功"),
  /** 部分成功（有跳过/失败文件）。 */
  PARTIAL("PARTIAL", "部分成功"),
  /** 生成失败。 */
  FAILED("FAILED", "生成失败");

  /** 状态码。 */
  private final String code;
  /** 状态描述。 */
  private final String description;
}
