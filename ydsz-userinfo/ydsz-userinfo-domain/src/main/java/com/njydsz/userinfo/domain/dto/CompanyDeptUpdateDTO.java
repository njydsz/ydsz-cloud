package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 公司-部门关联更新 DTO。
 *
 * <p>用于更新公司-部门关联关系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyDeptUpdateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 关联 ID（更新时必填） */
  @NotBlank(message = "关联 ID 不能为空")
  private String id;

  /** 公司 ID */
  private String companyId;

  /** 部门 ID */
  private String deptId;
}
