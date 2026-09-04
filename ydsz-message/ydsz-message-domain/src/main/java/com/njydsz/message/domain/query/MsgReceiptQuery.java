package com.njydsz.message.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息回执分页查询 Query。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MsgReceiptQuery extends PageQuery {

  /** 关联消息日志 ID */
  @Xss private String logId;

  /** 回执类型（DELIVERED/READ/CLICKED/FAILED） */
  @Xss private String receiptType;

  /** 供应商编码 */
  @Xss private String providerCode;
}
