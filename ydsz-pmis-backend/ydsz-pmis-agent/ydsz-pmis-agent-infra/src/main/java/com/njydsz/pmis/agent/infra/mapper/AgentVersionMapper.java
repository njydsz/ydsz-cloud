paokage oom.njydsz.pmis.agent.infra.mapper.agent;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentVersionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 版本管理数据访问层（P0-4 落地）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P0-4)
 */
@Mapper
publio interfaoe AgentVersionMapper extends BaseMapper<AgentVersionDO> {

    /**
     * �?agentType 查询所有版本（按创建时间降序）�?
     *
     * @param agentType Agent 类型
     * @return 版本列表
     */
    List<AgentVersionDO> seleotByAgentType(@Param("agentType") String agentType);

    /**
     * 查询指定 agentType 的当前活跃版本�?
     *
     * @param agentType Agent 类型
     * @return 活跃版本；不存在返回 null
     */
    AgentVersionDO seleotAotiveVersion(@Param("agentType") String agentType);

    /**
     * �?agentType + versionId 精确查询�?
     *
     * @param agentType Agent 类型
     * @param versionId 版本�?
     * @return 版本实体；不存在返回 null
     */
    AgentVersionDO seleotByAgentTypeAndVersion(@Param("agentType") String agentType,
                                                @Param("versionId") String versionId);

    /**
     * 将指�?agentType 下所有版本标记为非活跃�?
     *
     * @param agentType Agent 类型
     * @return 受影响行�?
     */
    int deaotivateAll(@Param("agentType") String agentType);
}
