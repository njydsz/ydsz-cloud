package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.agent.entity.base.DomainBaseEntity;

/**
 * Token 用量记录（domain 纯净 POJO，无 MP 注解）
 *
 * <p>记录每次 LLM 调用的 Token 消耗明细，用于成本核算与用量分析。
 *
 * <p><b>DDD 分层</b>：domain 层不携带 MyBatis-Plus 注解；
 * 持久化映射由 {@code infra.entity.TokenUsageRecordPO} 承担。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TokenUsageRecord extends DomainBaseEntity<String> {

  private static final long serialVersionUID = 1L;

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
