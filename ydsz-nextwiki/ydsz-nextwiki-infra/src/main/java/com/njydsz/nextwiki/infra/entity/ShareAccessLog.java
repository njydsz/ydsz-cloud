package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 分享链接访问日志实体。
 *
 * <p>记录每次分享链接被访问的详细信息，用于安全审计和访问统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_share_access_log")
public class ShareAccessLog extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 分享链接 ID */
  private String shareId;

  /** 分享码 */
  private String shareCode;

  /** 文件节点 ID */
  private String fileNodeId;

  /** 访问者用户 ID（匿名为空） */
  private String visitorId;

  /** 访问者名称 */
  private String visitorName;

  /** 访问者 IP 地址 */
  private String visitorIp;

  /** 访问者 User-Agent */
  private String userAgent;

  /** 访问类型：VIEW/DOWNLOAD/EDIT */
  private String accessType;

  /** 访问状态：SUCCESS/FAIL */
  private String accessStatus;

  /** 失败原因 */
  private String failReason;

  /** 访问时间 */
  private LocalDateTime accessTime;
}
