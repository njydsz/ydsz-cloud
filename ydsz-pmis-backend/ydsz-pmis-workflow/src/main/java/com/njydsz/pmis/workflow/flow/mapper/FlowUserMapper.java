package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程用户 Mapper
 *
 * <p>对应 pmis_flow_user 表，记录会签/或签场景下每个任务的处理人与处理状态。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowUserMapper extends BaseMapper<FlowUserDO> {

    /**
     * 查某 task 的所有用户
     */
    List<FlowUserDO> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 标记用户已处理
     */
    int markProcessed(@Param("taskId") Long taskId,
                      @Param("userId") String userId,
                      @Param("comment") String comment,
                      @Param("processAt") java.time.LocalDateTime processAt);
}
