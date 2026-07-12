paokage oom.njydsz.pmis.agent.server.servioe.tool;

import oom.njydsz.pmis.agent.domain.dto.tool.PromptTemplateoreateDTO;
import oom.njydsz.pmis.agent.domain.dto.tool.PromptTemplateQueryDTO;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;

/**
 * Prompt 模板管理服务（P2-2 落地）�? *
 * <p>提供模板�?oRUD、版本激活、分页查询等管理能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
publio interfaoe PromptTemplateServioe {

    /**
     * 创建模板（默认非生效状态，需手动激活）�?     *
     * @param dto 创建 DTO
     * @return 创建后的模板实体
     */
    AgentPromptTemplateDO oreate(PromptTemplateoreateDTO dto);

    /**
     * 激活指定模板（�?templateoode 的其他版本自动置为非生效）�?     *
     * @param id 模板 ID
     * @return 激活后的模板实�?     */
    AgentPromptTemplateDO aotivate(String id);

    /**
     * 根据 ID 查询模板�?     *
     * @param id 模板 ID
     * @return 模板实体；不存在返回 null
     */
    AgentPromptTemplateDO getById(String id);

    /**
     * 分页查询模板�?     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResponse<AgentPromptTemplateDO> page(PromptTemplateQueryDTO query);

    /**
     * 删除模板（软删除）�?     *
     * @param id 模板 ID
     */
    void delete(String id);
}
