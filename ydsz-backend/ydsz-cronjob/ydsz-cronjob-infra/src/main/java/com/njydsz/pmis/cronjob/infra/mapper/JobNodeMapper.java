package com.njydsz.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.job.JobNodeDO;

/**
 * 调度节点心跳 Mapper。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface JobNodeMapper extends BaseMapper<JobNodeDO> {

    /**
     * P0-8: 将超时仍为 ONLINE 的节点标记为 OFFLINE（僵尸节点回收）。
     *
     * <p>节点心跳超时（默认 30s 无心跳）但 status 仍为 ONLINE，
     * 说明节点未优雅下线（如 kill -9 / 宕机），需要由 Reaper 标记为 OFFLINE。
     *
     * @param cutoff 心跳截止时间（早于此时间的 ONLINE 节点视为僵尸）
     * @return 受影响行数
     */
    @Update("UPDATE ydsz_job_node SET status = 'OFFLINE' " +
            "WHERE status = 'ONLINE' AND last_heartbeat < #{cutoff}")
    int markStaleOnlineAsOffline(@Param("cutoff") LocalDateTime cutoff);

    /**
     * P1-3: 查询即将被标记为 OFFLINE 的僵尸节点 ID 列表（故障转移用）。
     *
     * <p>在 {@link #markStaleOnlineAsOffline} 执行前调用，
     * 获取所有心跳超时但仍为 ONLINE 的节点 ID，用于对这些节点上的 RUNNING 任务执行故障转移。
     *
     * @param cutoff 心跳截止时间（早于此时间的 ONLINE 节点视为僵尸）
     * @return 僵尸节点 ID 列表（nodeId）
     */
    @Select("SELECT node_id FROM ydsz_job_node " +
            "WHERE status = 'ONLINE' AND last_heartbeat < #{cutoff}")
    List<String> selectStaleOnlineNodeIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * P0-8: 物理删除已离线超过指定时长的节点记录。
     *
     * <p>清理 OFFLINE/DRAINING 状态且最后心跳超过 cutoff 的节点，
     * 避免 ydsz_job_node 表无限膨胀。
     *
     * @param cutoff 心跳截止时间（早于此时间的离线节点将被删除）
     * @return 受影响行数
     */
    @Delete("DELETE FROM ydsz_job_node " +
            "WHERE status IN ('OFFLINE', 'DRAINING') AND last_heartbeat < #{cutoff}")
    int deleteStaleOfflineNodes(@Param("cutoff") LocalDateTime cutoff);
}
