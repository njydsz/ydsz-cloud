package com.njydsz.pmis.agent.domain.entity.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent Prompt 模板表（P2-2 落地）。
 *
 * <p>存储 Agent 的 system / user prompt 模板，支持 {@code ${var}} 变量替换与版本管理。
 * 同一 {@code templateCode} 可有多版本，仅一条 {@code is_active=true} 生效。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_prompt_template")
public class AgentPromptTemplateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码（业务唯一，如 FLOW_GENERATOR_SYSTEM / REACT_FORMAT_INSTRUCTION） */
    private String templateCode;

    /** 模板名称（展示用） */
    private String templateName;

    /** Agent 类型（FLOW_GENERATOR / RISK_WARNING 等，通用模板为 COMMON） */
    private String agentType;

    /** Prompt 角色：SYSTEM / USER / REACT_FORMAT */
    private String promptRole;

    /** 模板内容，支持 ${var} 占位符 */
    private String content;

    /** 语义版本（如 1.0.0），支持版本回滚 */
    private String version;

    /** 是否当前生效（true=生效，同一 templateCode 仅一条为 true） */
    private Boolean isActive;

    /** 描述说明 */
    private String description;

    /** 租户 ID（单租户部署默认 1） */
    private String tenantId;
}
