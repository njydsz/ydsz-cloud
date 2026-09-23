package com.njydsz.generator.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板语法校验结果 VO。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateValidateVO {

  /** 是否通过校验。 */
  private Boolean isValid;
  /** 错误信息（校验通过时为空）。 */
  private String errorMessage;
  /** 错误行号（无错误时为 null）。 */
  private Integer errorLine;
}
