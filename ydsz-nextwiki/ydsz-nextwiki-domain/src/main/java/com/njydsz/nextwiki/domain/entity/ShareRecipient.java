package com.njydsz.nextwiki.domain.entity;

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
 * <p>记录分享链接的目标接收者，实现文件级定向分享能力。每条记录关联一条 {@link ShareLink}（{@link #shareId}），
 * 通过 {@link #recipientType} 标识接收者维度（USER/DEPT/ROLE），对应 {@link #recipientId} 和
 * {@link #recipientName}。状态机为 ACTIVE → VIEWED → REVOKED，{@link #viewedAt} 记录首次查看时间。
 *
 * <p><b>表名：</b>{@code ydsz_wiki_share_recipient}
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_share_recipient")
public class ShareRecipient extends MpBaseEntity<String> implements Serializable {

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
