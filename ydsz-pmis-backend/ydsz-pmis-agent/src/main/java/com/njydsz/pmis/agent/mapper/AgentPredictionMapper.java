package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentPredictionMapper extends BaseMapper<AgentPredictionDO> {

    AgentPredictionDO selectByTaskCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<AgentPredictionDO> selectByBiz(@Param("bizType") String bizType,
                                        @Param("bizId") Long bizId,
                                        @Param("agentType") String agentType);

    List<AgentPredictionDO> selectByAgentType(@Param("agentType") String agentType,
                                              @Param("alertLevel") String alertLevel,
                                              @Param("limit") Integer limit);

    List<Map<String, Object>> aggregateByType(@Param("tenantId") Long tenantId);

    long countByAlertLevel(@Param("alertLevel") String alertLevel,
                           @Param("agentType") String agentType,
                           @Param("tenantId") Long tenantId);
}
