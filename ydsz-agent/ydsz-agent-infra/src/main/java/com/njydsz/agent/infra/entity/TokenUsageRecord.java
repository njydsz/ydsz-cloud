package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Token 用量记录（映射 ydsz_agt_token_usage 表）
 *
 * <p>记录每次 LLM 调用的 Token 消耗明细，用于成本核算与用量分析。 每次 LLM 调用完成后异步写入，按 conversationId + createdAt 建立索引。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变持久化实体； 仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_token_usage")
public class TokenUsageRecord extends MpBaseEntity<String> {

  /** 所属对话 ID（关联 ydsz_agent_conversation） */
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
