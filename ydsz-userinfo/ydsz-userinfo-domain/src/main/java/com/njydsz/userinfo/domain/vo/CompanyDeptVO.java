package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 公司-部门关联 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyDeptVO {

  /** 关联唯一标识 */
  private String id;

  /** 公司 ID */
  private String companyId;

  /** 部门 ID */
  private String deptId;
}
