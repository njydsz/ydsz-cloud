package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Token 用量记录（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>记录每次 LLM 调用的 Token 消耗明细，用于成本核算与用量分析。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_token_usage")
public class TokenUsageRecord extends MpBaseEntity<String> {

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
