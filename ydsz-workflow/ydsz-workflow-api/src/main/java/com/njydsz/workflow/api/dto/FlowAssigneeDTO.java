package com.njydsz.workflow.api.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 办理人 DTO（API 契约层）。
 *
 * <p>定义 Feign 客户端接口的参数类型，供跨服务调用方引用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAssigneeDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户类型：USER/ROLE/DEPT */
  @NotNull private String userType;

  /** 用户/角色/部门 ID */
  @NotNull private String userId;

  /** 姓名 */
  private String userName;
}
