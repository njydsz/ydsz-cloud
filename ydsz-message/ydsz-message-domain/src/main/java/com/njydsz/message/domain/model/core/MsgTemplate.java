package com.njydsz.message.domain.model.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.template.TemplateAuditStatusEnum;
import com.njydsz.message.domain.enums.template.TemplateStatusEnum;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息模板领域实体 — 支持 {@code ${var}} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 分类 / 场景。
 *
 * <p>对应数据库表 {@code ydsz_msg_template}。与 {@code MsgTemplateDO} 的区别：
 *
 * <ul>
 *   <li>去除 MyBatis-Plus 持久化注解（{@code @TableName} 等）
 *   <li>通道/状态/审核状态字段使用枚举类型替代 String
 *   <li>不继承 {@code MpBaseEntity}，审计字段平铺定义
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class MsgTemplate implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ===== 审计字段 =====

  /** 主键 ID（雪花算法） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 创建人 ID */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 ID */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 删除标识: false 未删除 / true 已删除 */
  private Boolean deleted;

  // ===== 业务字段 =====

  /** 模板编码(同 code 不同 channel/locale 形成多版本) */
  private String templateCode;

  /** 通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
  private MessageChannelEnum channel;

  /** 语言区域(如 zh-CN / en-US),影响 i18n 模板选择 */
  private String locale;

  /** 语义版本(如 1.0.0),支持模板版本回滚 */
  private String version;

  /** 模板分类(如 ALERT/APPROVAL/NOTICE/VERIFY) */
  private String category;

  /** 场景编码(如 BUDGET_YELLOW / CONTRACT_SIGN),用于业务侧精确匹配 */
  private String sceneCode;

  /** 主题(EMAIL 专用) */
  private String subject;

  /** 模板内容,支持 ${var} 占位符 */
  private String content;

  /** 供应商(如 aliyun/tencent) */
  private String provider;

  /** 供应商侧模板 ID */
  private String providerKey;

  /** 短信签名 */
  private String signName;

  /** 状态: ENABLED 启用 / DISABLED 禁用 */
  private TemplateStatusEnum status;

  /** 审核状态: DRAFT 草稿 / AUDITING 审核中 / APPROVED 已通过 / REJECTED 已驳回 */
  private TemplateAuditStatusEnum auditStatus;

  /** 审核人 ID */
  private String auditBy;

  /** 审核时间 */
  private LocalDateTime auditAt;

  /** 审核备注 */
  private String auditRemark;

  /** 描述说明 */
  private String description;

  /** 模板变量定义 JSON(变量名→类型/必填/默认值/枚举值) */
  private String variableDefs;
}
