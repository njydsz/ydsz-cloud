package com.njydsz.agent.infra.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Agent 定义持久化对象（映射 ydsz_agt_definition 表）。
 *
 * <p>基础设施层 PO，包含 MyBatis-Plus 持久化注解。
 * 对应的领域模型 {@code domain.entity.AgentDefinition} 为纯净 POJO。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_definition")
public class AgentDefinitionPO extends MpBaseEntity<String> {

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

  /** 模型配置 JSON */
  private String modelConfig;

  /** 工具名称列表 JSON */
  private String toolNames;

  /** 温度参数 */
  private BigDecimal temperature;

  /** 最大生成 Token 数 */
  private Integer maxTokens;
}
