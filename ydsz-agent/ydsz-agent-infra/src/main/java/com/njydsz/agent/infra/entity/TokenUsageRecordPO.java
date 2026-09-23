package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Token 用量记录持久化对象（映射 ydsz_agt_token_usage 表）。
 *
 * <p>基础设施层 PO，包含 MyBatis-Plus 持久化注解。
 * 对应的领域模型 {@code domain.entity.TokenUsageRecord} 为纯净 POJO。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_token_usage")
public class TokenUsageRecordPO extends MpBaseEntity<String> {

  /** 所属对话 ID */
  private String conversationId;

  /** 使用的模型标识 */
  private String modelName;

  /** 提示词 Token 数 */
  private Long promptTokens;

  /** 补全 Token 数 */
  private Long completionTokens;

  /** 总 Token 数 */
  private Long totalTokens;
}
