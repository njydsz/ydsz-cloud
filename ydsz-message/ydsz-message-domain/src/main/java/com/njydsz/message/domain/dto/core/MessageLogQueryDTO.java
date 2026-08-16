package com.njydsz.message.domain.dto.core;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.safe.annotation.Xss;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息日志分页查询 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageLogQueryDTO extends PageQuery {

  /** 通道 */
  @Xss private String channel;

  /** 业务类型 */
  @Xss private String bizType;

  /** 业务单据 ID */
  @Xss private String bizId;

  /** 发送状态 */
  @Xss private String status;

  /** 接收人 */
  @Xss private String receiver;

  /** 发送优先级 */
  @Xss private String priority;

  /** 撤回状态 */
  @Xss private String recallStatus;

  // tenantId 已由父类 BaseQuery 提供，此处不再重复声明

  /** P2-13: 全文搜索关键词（模糊匹配 content / receiver / templateCode） */
  @Xss private String keyword;

  /** P2-13: 消息分组（按业务分组筛选） */
  @Xss private String messageGroup;

  /** P2-13: 时间范围开始 */
  @Xss private String startTime;

  /** P2-13: 时间范围结束 */
  @Xss private String endTime;
}
