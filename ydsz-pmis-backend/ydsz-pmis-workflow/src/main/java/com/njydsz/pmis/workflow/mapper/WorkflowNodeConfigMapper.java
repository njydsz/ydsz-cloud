package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.WorkflowNodeConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点配置 Mapper
 */
@Mapper
public interface WorkflowNodeConfigMapper extends BaseMapper<WorkflowNodeConfigDO> {

    /**
     * 根据流程 KEY 与节点 ID 查询
     */
    WorkflowNodeConfigDO selectByNode(@Param("processKey") String processKey,
                                      @Param("nodeId") String nodeId,
                                      @Param("tenantId") Long tenantId);

    /**
     * 根据流程 KEY 查询所有节点配置
     */
    List<WorkflowNodeConfigDO> selectByProcessKey(@Param("processKey") String processKey,
                                                  @Param("tenantId") Long tenantId);
}
