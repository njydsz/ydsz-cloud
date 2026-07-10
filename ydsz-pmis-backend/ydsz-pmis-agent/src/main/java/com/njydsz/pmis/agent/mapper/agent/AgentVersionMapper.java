package com.njydsz.pmis.agent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.agent.AgentVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 版本管理数据访问层（P0-4 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P0-4)
 */
@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionDO> {

    /**
     * 按 agentType 查询所有版本（按创建时间降序）。
     *
     * @param agentType Agent 类型
     * @return 版本列表
     */
    List<AgentVersionDO> selectByAgentType(@Param("agentType") String agentType);

    /**
     * 查询指定 agentType 的当前活跃版本。
     *
     * @param agentType Agent 类型
     * @return 活跃版本；不存在返回 null
     */
    AgentVersionDO selectActiveVersion(@Param("agentType") String agentType);

    /**
     * 按 agentType + versionId 精确查询。
     *
     * @param agentType Agent 类型
     * @param versionId 版本号
     * @return 版本实体；不存在返回 null
     */
    AgentVersionDO selectByAgentTypeAndVersion(@Param("agentType") String agentType,
                                                @Param("versionId") String versionId);

    /**
     * 将指定 agentType 下所有版本标记为非活跃。
     *
     * @param agentType Agent 类型
     * @return 受影响行数
     */
    int deactivateAll(@Param("agentType") String agentType);
}
