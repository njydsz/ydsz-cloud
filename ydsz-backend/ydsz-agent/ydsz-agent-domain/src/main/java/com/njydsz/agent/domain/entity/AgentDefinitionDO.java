package com.njydsz.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 定义 DO（映射 ydsz_agent_definition 表）
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@TableName("ydsz_agent_definition")
public class AgentDefinitionDO {

    @TableId
    private String id;

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

    private String status;

    @TableLogic
    private Boolean deleted;

    private String tenantId;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
