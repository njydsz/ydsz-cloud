package com.njydsz.pmis.agent.dto.tool;

import lombok.Data;

/**
 * Prompt 模板查询 DTO（P2-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Data
public class PromptTemplateQueryDTO {

    /** 模板编码（模糊匹配） */
    private String templateCode;

    /** Agent 类型 */
    private String agentType;

    /** Prompt 角色 */
    private String promptRole;

    /** 是否仅查询生效模板 */
    private Boolean isActive;

    /** 页码（从 1 开始） */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;
}
