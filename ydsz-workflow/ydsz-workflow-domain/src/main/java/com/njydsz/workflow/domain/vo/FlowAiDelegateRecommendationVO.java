package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * AI 委派推荐视图对象。
 *
 * <p>用于返回 AI 推荐的委派目标信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAiDelegateRecommendationVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 推荐人 ID */
  private String userId;

  /** 推荐人姓名 */
  private String userName;

  /** 推荐得分（0-100） */
  private int score;

  /** 推荐理由 */
  private String reason;
}
