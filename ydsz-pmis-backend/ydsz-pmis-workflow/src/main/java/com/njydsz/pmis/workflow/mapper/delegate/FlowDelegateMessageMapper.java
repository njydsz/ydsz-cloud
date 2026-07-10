package com.njydsz.pmis.workflow.mapper.delegate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.delegate.FlowDelegateMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 自建工作流引擎 - 委派沟通记录 Mapper
 *
 * <p>P2-1 (GAP-08): 委托人与被委托人之间的留言持久化与查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowDelegateMessageMapper extends BaseMapper<FlowDelegateMessageDO> {

    /**
     * 查询某任务下的沟通记录（按创建时间升序）
     *
     * @param taskId 任务 ID
     * @return 沟通记录列表
     */
    @Select("SELECT * FROM pmis_flow_delegate_message "
            + "WHERE task_id = #{taskId} AND deleted = 0 ORDER BY created_at ASC")
    List<FlowDelegateMessageDO> selectByTask(@Param("taskId") String taskId);

    /**
     * 查询某实例下的沟通记录
     *
     * @param instanceId 实例 ID
     * @return 沟通记录列表
     */
    @Select("SELECT * FROM pmis_flow_delegate_message "
            + "WHERE instance_id = #{instanceId} AND deleted = 0 ORDER BY created_at ASC")
    List<FlowDelegateMessageDO> selectByInstance(@Param("instanceId") String instanceId);

    /**
     * 标记任务下指定角色（OWNER/DELEGATE）所有未读消息为已读
     *
     * @param taskId      任务 ID
     * @param viewerRole  查看方角色: OWNER / DELEGATE
     * @return 受影响行数
     */
    @Update("UPDATE pmis_flow_delegate_message "
            + "SET read_flag = 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE task_id = #{taskId} AND sender_role = #{viewerRole} "
            + "AND read_flag = 0 AND deleted = 0")
    int markRead(@Param("taskId") String taskId, @Param("viewerRole") String viewerRole);
}
