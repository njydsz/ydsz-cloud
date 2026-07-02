package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowDelegateLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程委派代理日志 Mapper
 *
 * <p>P1-4: 审计追溯代理操作。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface FlowDelegateLogMapper extends BaseMapper<FlowDelegateLogDO> {

    /**
     * 按任务 ID 查日志
     */
    List<FlowDelegateLogDO> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 按代理人查日志（"我代理处理了哪些任务"）
     */
    List<FlowDelegateLogDO> selectByDelegateUser(@Param("delegateUserId") Long delegateUserId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /**
     * 按授权人查日志（"我的哪些任务被代理了"）
     */
    List<FlowDelegateLogDO> selectByOwnerUser(@Param("ownerUserId") Long ownerUserId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);
}
