package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息模板版本历史实体，记录模板每次审核通过/拒绝的版本快照。
 *
 * <p>对应数据库表 {@code ydsz_msg_template_version}。每次模板内容变更并审核通过后，
 * 自动插入一条版本记录（version 自增），content/variableDefs 保存完整快照，
 * 支持版本回滚和历史对比。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
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
