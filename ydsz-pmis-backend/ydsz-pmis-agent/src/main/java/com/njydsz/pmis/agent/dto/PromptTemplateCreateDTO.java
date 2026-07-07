package com.njydsz.pmis.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Prompt 模板创建/更新 DTO（P2-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Data
public class PromptTemplateCreateDTO {

    /** 模板编码（业务唯一） */
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /** Agent 类型（FLOW_GENERATOR / COMMON 等） */
    @NotBlank(message = "Agent 类型不能为空")
    private String agentType;

    /** Prompt 角色：SYSTEM / USER / REACT_FORMAT */
    @NotBlank(message = "Prompt 角色不能为空")
    private String promptRole;

    /** 模板内容，支持 ${var} 占位符 */
    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 语义版本（如 1.0.0），不填默认 1.0.0 */
    private String version;

    /** 描述说明 */
    private String description;
}
