package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 用户-部门关联视图对象。
 *
 * <p>表示用户与部门之间的多对多关联关系。一个用户可以属于多个部门（矩阵式组织架构），
 * isPrimary 字段标识主部门（用于审批等场景的默认归属）。
 *
 * <p>不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 26.09.01
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
