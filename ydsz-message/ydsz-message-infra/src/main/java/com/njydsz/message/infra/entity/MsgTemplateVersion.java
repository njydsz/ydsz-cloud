package com.njydsz.message.infra.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息模板版本历史实体。
 *
 * <p>P1-6: 记录模板每次审核通过/拒绝的版本快照，支持版本回滚和历史对比。 每次模板内容变更并审核通过后，自动插入一条版本记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_template_version")
public class MsgTemplateVersion extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 模板编码（关联 ydsz_msg_template.code） */
  private String templateCode;

  /** 版本号（每次审核通过递增，如 1, 2, 3） */
  private Integer version;

  /** 模板内容快照 */
  private String content;

  /** 模板变量定义快照（JSON） */
  private String variableDefs;

  /** 审核状态: APPROVED / REJECTED */
  private String auditStatus;

  /** 审核人 */
  private String auditor;

  /** 审核意见 */
  private String auditRemark;
}
