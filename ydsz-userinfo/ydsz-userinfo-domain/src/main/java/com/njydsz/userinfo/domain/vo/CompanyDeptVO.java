package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 公司-部门关联视图对象。
 *
 * <p>表示公司与部门之间的多对多关联关系。一个公司可以包含多个部门，一个部门也可以从属于多个公司
 * （适用于集团管控场景下的跨公司部门共享）。
 *
 * <p>不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 26.09.01
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
