package com.njydsz.pmis.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowTimerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流定时器 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface FlowTimerMapper extends BaseMapper<FlowTimerDO> {

    /**
     * 扫描到点的 PENDING 定时器（status = PENDING AND fire_at <= now AND deleted = 0）
     *
     * @param now 当前时间
     * @param limit 单次扫描上限
     */
    List<FlowTimerDO> selectDueTimers(@Param("now") LocalDateTime now,
                                      @Param("limit") int limit);

    /**
     * 关闭某 userTask 关联的所有 BOUNDARY 定时器（CANCELLED）
     *
     * @param boundaryTaskId userTask ID
     * @param reason 取消原因
     * @return 受影响行数
     */
    int cancelByTask(@Param("boundaryTaskId") String boundaryTaskId,
                     @Param("reason") String reason);

    /**
     * 标记定时器已触发
     */
    int markFired(@Param("id") String id,
                  @Param("firedAt") LocalDateTime firedAt);

    /**
     * 关闭某实例所有 PENDING 定时器（实例终止/驳回时使用）
     */
    int cancelByInstance(@Param("instanceId") String instanceId,
                         @Param("reason") String reason);

    /**
     * 统计实例的 PENDING 定时器数（用于检查流程是否被定时器阻塞）
     */
    long countPendingByInstance(@Param("instanceId") String instanceId);
}
