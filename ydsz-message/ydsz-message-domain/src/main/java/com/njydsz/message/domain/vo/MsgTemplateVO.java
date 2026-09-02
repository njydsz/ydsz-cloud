package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息模板视图对象（VO）。
 *
 * <p>用于 Controller 层返回消息模板的完整信息，包含模板内容、通道配置、 供应商绑定、审核状态及变量定义，支撑模板管理与多语言配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgTemplateVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 模板唯一标识（主键） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 模板编码，业务唯一 */
  private String templateCode;

  /** 通道（SMS/EMAIL/WEBHOOK/WECHAT/INSITE） */
  private String channel;

  /** 语言区域（如 zh_CN/en_US） */
  private String locale;

  /** 版本号 */
  private String version;

  /** 分类 */
  private String category;

  /** 场景编码 */
  private String sceneCode;

  /** 主题（邮件标题/短信签名+正文） */
  private String subject;

  /** 模板内容 */
  private String content;

  /** 供应商（ALIYUN/TENCENT/HUAWEI） */
  private String provider;

  /** 供应商侧模板 ID */
  private String providerKey;

  /** 签名名称 */
  private String signName;

  /** 状态（ENABLED/DISABLED） */
  private String status;

  /** 审核状态（PENDING/APPROVED/REJECTED） */
  private String auditStatus;

  /** 审核人 */
  private String auditBy;

  /** 审核时间 */
  private LocalDateTime auditAt;

  /** 审核备注 */
  private String auditRemark;

  /** 模板描述 */
  private String description;

  /** 变量定义 JSON */
  private String variableDefs;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
