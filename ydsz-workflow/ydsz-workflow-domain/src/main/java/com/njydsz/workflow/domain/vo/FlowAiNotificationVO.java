package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * AI 优化通知内容视图对象。
 *
 * <p>用于返回 AI 优化后的审批通知文案。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAiNotificationVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 通知标题 */
  private String title;

  /** 通知内容 */
  private String content;

  /** 是否经过 AI 优化（false 表示降级结果） */
  private boolean optimized;

  /** 降级原因（optimized=false 时） */
  private String fallbackReason;
}
