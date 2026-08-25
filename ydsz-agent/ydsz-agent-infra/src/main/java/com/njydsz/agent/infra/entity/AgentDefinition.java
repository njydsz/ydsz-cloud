package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Agent 定义（映射 ydsz_agent_definition 表）
 *
 * <p>存储 Agent 的完整配置信息，包括类型、系统提示词、绑定工具、模型参数等。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变持久化实体；仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agent_definition")
public class AgentDefinition extends MpBaseEntity<String> {

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

  /** 温度参数 */
  private Double temperature;

  /** 最大生成 Token 数 */
  private Integer maxTokens;
}
