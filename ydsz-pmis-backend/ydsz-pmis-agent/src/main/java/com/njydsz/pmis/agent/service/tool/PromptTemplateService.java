package com.njydsz.pmis.agent.service;

import com.njydsz.pmis.agent.dto.tool.PromptTemplateCreateDTO;
import com.njydsz.pmis.agent.dto.tool.PromptTemplateQueryDTO;
import com.njydsz.pmis.agent.entity.agent.AgentPromptTemplateDO;
import com.njydsz.pmis.common.api.PageResult;

/**
 * Prompt 模板管理服务（P2-2 落地）。
 *
 * <p>提供模板的 CRUD、版本激活、分页查询等管理能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
public interface PromptTemplateService {

    /**
     * 创建模板（默认非生效状态，需手动激活）。
     *
     * @param dto 创建 DTO
     * @return 创建后的模板实体
     */
    AgentPromptTemplateDO create(PromptTemplateCreateDTO dto);

    /**
     * 激活指定模板（同 templateCode 的其他版本自动置为非生效）。
     *
     * @param id 模板 ID
     * @return 激活后的模板实体
     */
    AgentPromptTemplateDO activate(String id);

    /**
     * 根据 ID 查询模板。
     *
     * @param id 模板 ID
     * @return 模板实体；不存在返回 null
     */
    AgentPromptTemplateDO getById(String id);

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AgentPromptTemplateDO> page(PromptTemplateQueryDTO query);

    /**
     * 删除模板（软删除）。
     *
     * @param id 模板 ID
     */
    void delete(String id);
}
