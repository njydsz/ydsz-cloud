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
  RUNNING("RUNNING", "generator.genstatus.running"),
  /** 全部生成成功。 */
  SUCCESS("SUCCESS", "generator.genstatus.success"),
  /** 部分成功（有跳过/失败文件）。 */
  PARTIAL("PARTIAL", "generator.genstatus.partial"),
  /** 生成失败。 */
  FAILED("FAILED", "generator.genstatus.failed");

  /** 状态码。 */
  private final String code;
  /** 状态描述。 */
  private final String description;
}
