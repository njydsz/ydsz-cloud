package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息模板 DTO（命令请求参数）。
 *
 * <p>用于 Repository 层 CUD 操作的统一入参，不区分 Create / Update：
 * <ul>
 *   <li>创建场景：{@code id} 字段不传</li>
 *   <li>更新场景：传入 {@code id}</li>
 * </ul>
 *
 * <p>遵循云顶编码规范第 34 节：domain 层 DTO 不区分 Create/Update。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgTemplateDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 模板唯一标识（主键，更新时传入） */
  private String id;

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

  /** 租户 ID */
  private String tenantId;

  /** 删除标识 */
  private Boolean deleted;
}
