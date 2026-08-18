package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 公司-部门关联 DTO。
 *
 * <p>用于创建公司-部门关联关系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyDeptDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 公司 ID */
  private String companyId;

  /** 部门 ID */
  private String deptId;
}
