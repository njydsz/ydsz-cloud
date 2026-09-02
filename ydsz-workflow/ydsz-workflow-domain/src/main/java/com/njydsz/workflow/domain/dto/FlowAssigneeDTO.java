package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;

/**
 * 办理人 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowAssigneeDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户类型：USER/ROLE/DEPT */
  @NotNull private String userType;

  /** 用户/角色/部门 ID */
  @NotNull private String userId;

  /** 姓名 */
  @SensitiveData(SensitiveType.CHINESE_NAME)
  private String userName;
}
