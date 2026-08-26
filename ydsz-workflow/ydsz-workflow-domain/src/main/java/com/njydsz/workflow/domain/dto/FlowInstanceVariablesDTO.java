package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程实例变量批量写入 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类型 DTO + JSR-303 校验。 variables 保持 Map 类型（动态流程变量）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "流程实例变量 DTO")
public class FlowInstanceVariablesDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程变量（动态键值对，保持 Map 类型） */
  @NotNull(message = "{validation.workflow.instance.variables.required}")
  private Map<String, Object> variables;
}
