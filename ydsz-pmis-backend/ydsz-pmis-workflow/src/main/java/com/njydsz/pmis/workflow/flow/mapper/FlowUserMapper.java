package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程用户 Mapper
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
