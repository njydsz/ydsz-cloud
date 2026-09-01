package com.njydsz.message.domain.dto;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息撤回请求 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RecallRequestDTO {

  /** 消息/通知 ID */
  @Xss private String id;

  /** 业务类型 */
  @Xss private String bizType;

  /** 业务单据 ID */
  @Xss private String bizId;

  /** 撤回范围: SINGLE 单条 / BATCH 批次 */
  @Xss private String recallScope;
}
