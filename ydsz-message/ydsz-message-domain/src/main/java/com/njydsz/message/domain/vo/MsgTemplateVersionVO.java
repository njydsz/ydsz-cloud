package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息模板版本历史视图对象（VO）。
 *
 * <p>用于返回模板版本历史的完整信息，包含版本号、内容快照及审核信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgTemplateVersionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 版本记录唯一标识（主键） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 模板编码 */
  private String templateCode;

  /** 版本号 */
  private Integer version;

  /** 模板内容快照 */
  private String content;

  /** 模板变量定义快照（JSON） */
  private String variableDefs;

  /** 审核状态（APPROVED/REJECTED） */
  private String auditStatus;

  /** 审核人 */
  private String auditor;

  /** 审核意见 */
  private String auditRemark;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
