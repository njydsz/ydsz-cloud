package com.njydsz.pmis.agent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.agent.AgentPromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent Prompt 模板数据访问层（P2-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Mapper
public interface AgentPromptTemplateMapper extends BaseMapper<AgentPromptTemplateDO> {

    /**
     * 查询指定模板编码的当前生效版本。
     *
     * @param templateCode 模板编码
     * @return 生效的模板实体；不存在返回 null
     */
    AgentPromptTemplateDO selectActiveByCode(@Param("templateCode") String templateCode);

    /**
     * 将指定模板编码的其他版本置为非生效（用于激活新版本时排他）。
     *
     * @param templateCode 模板编码
     * @param excludeId    排除的模板 ID（即新激活的模板 ID）
     * @return 受影响行数
     */
    int deactivateOthers(@Param("templateCode") String templateCode,
                         @Param("excludeId") String excludeId);
}
