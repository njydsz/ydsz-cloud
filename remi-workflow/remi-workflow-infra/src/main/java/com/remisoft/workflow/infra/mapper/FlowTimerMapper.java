package com.remisoft.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.workflow.domain.entity.FlowTimer;

/**
 * 工作流定时器 Mapper
 *
 * <p>对应数据表 <code>remi_flow_timer</code>，存储工作流中的定时器配置（超时/催办/自动跳过）。</p>
 * <p>定时器由 {@code FlowTimerScheduler} 周期性扫描触发（每分钟），执行超时自动通过/催办通知/自动跳过等动作。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_timer_id — 定时器 ID 唯一索引</li>
 *   <li>idx_fire_time — 触发时间排序索引（扫描待触发定时器）</li>
 *   <li>idx_status — 状态过滤索引（PENDING/FIRED/CANCELLED）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.workflow.domain.entity.FlowTimer 定时器实体
 * @see com.remisoft.workflow.server.scheduler.FlowTimerScheduler 定时器调度器
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowTimerMapper extends BaseMapper<FlowTimer> {

    /**
     * 扫描到点的 PENDING 定时器（status = PENDING AND fire_at <= now AND deleted = 0）
     *
     * @param now 当前时间
     * @param limit 单次扫描上限
     */
    List<FlowTimer> selectDueTimers(@Param("now") LocalDateTime now,
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
