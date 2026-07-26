package com.njydsz.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Agent 定义 DO（映射 ydsz_agent_definition 表）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agent_definition")
public class AgentDefinitionDO extends MpBaseEntity<String> {

    private String agentCode;

    private String agentName;

    private String agentType;

    private String description;

    private String systemPrompt;

    /** 模型配置 JSON（temperature/maxTokens/modelId 等） */
    private String modelConfig;

    /** 工具名称列表 JSON（["tool1","tool2"]） */
    private String toolNames;

    private Double temperature;

    private Integer maxTokens;

    private String tenantId;
}
