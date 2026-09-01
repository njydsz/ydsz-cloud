package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 公司-部门关联 DTO。
 *
 * <p>表示公司与部门之间的多对多关联关系，适用于集团管控场景下的跨公司部门共享。
 * 同时用于创建和更新场景：创建时 {@code id} 可不传，更新时 {@code id} 必填。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyDeptDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 关联 ID（更新时必填） */
  private String id;

  /** 公司 ID */
  private String companyId;

  /** 部门 ID */
  private String deptId;
}
