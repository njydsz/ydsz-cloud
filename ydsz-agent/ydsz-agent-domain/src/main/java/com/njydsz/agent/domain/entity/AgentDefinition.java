package com.njydsz.agent.domain.entity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Agent 定义（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>存储 Agent 的完整配置信息，包括类型、系统提示词、绑定工具、模型参数等。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_definition")
public class AgentDefinition extends MpBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** Agent 编码（业务唯一键） */
  private String agentCode;

  /** Agent 名称（展示用） */
  private String agentName;

  /** Agent 类型（CHAT/REACT/RAG/PLAN_EXECUTE/ROUTER） */
  private String agentType;

  /** Agent 描述 */
  private String description;

  /** 系统提示词 */
  private String systemPrompt;

  /** 模型配置 JSON（temperature/maxTokens/modelId 等） */
  private String modelConfig;

  /** 工具名称列表 JSON（["tool1","tool2"]） */
  private String toolNames;

  /** 温度参数（LLM 采样温度，范围 0~2） */
  private BigDecimal temperature;

  /** 最大生成 Token 数 */
  private Integer maxTokens;
}
