package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 分享目标用户实体（定向分享）。
 *
 * <p>记录分享链接的目标接收者，支持指定用户/部门/角色分享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_share_recipient")
public class ShareRecipientDO extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 分享链接 ID */
  private String shareId;

  /** 接收者类型：USER/DEPT/ROLE */
  private String recipientType;

  /** 接收者 ID */
  private String recipientId;

  /** 接收者名称 */
  private String recipientName;

  /** 状态：ACTIVE/VIEWED/REVOKED */
  private String status;

  /** 首次查看时间 */
  private LocalDateTime viewedAt;
}
