package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 公司 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyVO {

  /** 公司唯一标识 */
  private String id;

  /** 公司名称 */
  private String companyName;

  /** 公司编码，全局唯一 */
  private String companyCode;

  /** 父公司 ID */
  private String parentId;

  /** 联系人 */
  private String contactPerson;

  /** 联系电话 */
  private String contactPhone;

  /** 地址 */
  private String address;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;
}
