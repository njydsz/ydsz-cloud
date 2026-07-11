package com.njydsz.pmis.workflow.infra.mapper.integration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.integration.FlowAttachmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 自建工作流引擎 - 审批附件 Mapper
 *
 * <p>P1-6 (GAP-51)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowAttachmentMapper extends BaseMapper<FlowAttachmentDO> {

    /**
     * 查询某任务关联的未删除附件
     *
     * @param taskId 任务 ID
     * @return 附件列表
     */
    @Select("SELECT * FROM pmis_flow_attachment WHERE task_id = #{taskId} AND deleted = 0 ORDER BY created_at ASC")
    List<FlowAttachmentDO> selectByTask(@Param("taskId") String taskId);

    /**
     * 查询某实例关联的未删除附件
     *
     * @param instanceId 实例 ID
     * @return 附件列表
     */
    @Select("SELECT * FROM pmis_flow_attachment WHERE instance_id = #{instanceId} AND deleted = 0 ORDER BY created_at ASC")
    List<FlowAttachmentDO> selectByInstance(@Param("instanceId") String instanceId);
}
