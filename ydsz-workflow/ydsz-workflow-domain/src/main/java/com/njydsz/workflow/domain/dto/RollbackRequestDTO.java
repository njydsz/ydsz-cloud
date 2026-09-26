package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程定义回滚请求 DTO
 *
 * <p>一键回滚流程定义到上一版本的入参载体，对应 {@code POST /definition/rollback} 请求体。
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@Data
public class RollbackRequestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码（业务唯一键，租户内唯一） */
  @NotBlank(message = "流程编码不能为空")
  private String flowCode;
}
