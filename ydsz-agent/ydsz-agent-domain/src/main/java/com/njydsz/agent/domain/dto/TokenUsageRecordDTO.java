package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Token 用量记录 DTO。
 *
 * <p>用于创建 Token 用量记录。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TokenUsageRecordDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 所属对话 ID（关联 ydsz_agt_conversation） */
  private String conversationId;

  /** 使用的模型标识 */
  private String modelName;

  /** 提示词 Token 数 */
  private Long promptTokens;

  /** 补全 Token 数 */
  private Long completionTokens;

  /** 总 Token 数（prompt + completion） */
  private Long totalTokens;
}
