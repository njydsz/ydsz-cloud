package com.njydsz.message.domain.dto;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 模板审核 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TemplateAuditDTO {

  /** 模板 ID */
  @Xss private String id;

  /** 审核状态: DRAFT/AUDITING/APPROVED/REJECTED */
  @Xss private String auditStatus;

  /** 审核备注 */
  @Xss private String auditRemark;
}
