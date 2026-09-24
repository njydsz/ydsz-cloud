package com.njydsz.nextwiki.domain.entity;

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
 * <p>记录每次分享链接被访问的详细信息，用于安全审计和访问统计。每条日志关联一条分享链接（{@link #shareId}）
 * 和被访问文件节点（{@link #fileNodeId}），捕获访问者身份（{@link #visitorId} / {@link #visitorName}）、
 * 客户端 IP（{@link #visitorIp}）、浏览器标识（{@link #userAgent}）、访问类型（VIEW/DOWNLOAD/EDIT）、
 * 访问结果（SUCCESS/FAIL）及失败原因（{@link #failReason}）。
 *
 * <p><b>表名：</b>{@code ydsz_wiki_share_access_log}
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_share_access_log")
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
