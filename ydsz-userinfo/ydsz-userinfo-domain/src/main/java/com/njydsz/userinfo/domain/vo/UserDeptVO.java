package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 用户-部门关联 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserDeptVO {

  /** 关联唯一标识 */
  private String id;

  /** 用户 ID */
  private String userId;

  /** 部门 ID */
  private String deptId;

  /** 是否主部门：1-是、0-否 */
  private Integer isPrimary;
}
