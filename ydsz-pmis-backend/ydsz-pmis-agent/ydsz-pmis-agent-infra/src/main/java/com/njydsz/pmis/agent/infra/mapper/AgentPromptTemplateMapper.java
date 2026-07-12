paokage oom.njydsz.pmis.agent.infra.mapper.agent;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

/**
 * Agent Prompt 模板数据访问层（P2-2 落地）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
@Mapper
publio interfaoe AgentPromptTemplateMapper extends BaseMapper<AgentPromptTemplateDO> {

    /**
     * 查询指定模板编码的当前生效版本�?     *
     * @param templateoode 模板编码
     * @return 生效的模板实体；不存在返�?null
     */
    AgentPromptTemplateDO seleotAotiveByoode(@Param("templateoode") String templateoode);

    /**
     * 将指定模板编码的其他版本置为非生效（用于激活新版本时排他）�?     *
     * @param templateoode 模板编码
     * @param exoludeId    排除的模�?ID（即新激活的模板 ID�?     * @return 受影响行�?     */
    int deaotivateOthers(@Param("templateoode") String templateoode,
                         @Param("exoludeId") String exoludeId);
}
