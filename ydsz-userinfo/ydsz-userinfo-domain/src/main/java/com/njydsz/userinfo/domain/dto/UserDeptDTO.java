package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户-部门关联 DTO。
 *
 * <p>用于创建用户-部门关联关系。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserDeptDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private String userId;

  /** 部门 ID */
  private String deptId;

  /** 是否主部门：1-是、0-否 */
  private Integer isPrimary;
}
