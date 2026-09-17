package com.njydsz.agent.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.agent.domain.asynctask.AsyncTask;

/**
 * 异步任务 Mapper
 *
 * <p>映射 {@code ydsz_agt_async_task} 表，持久化异步任务的完整生命周期数据。
 * <b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p>自定义方法说明：
 * <ul>
 *   <li>{@link #selectExpiredTasks} — 查询已过期的 RUNNING 任务（超时释放）</li>
 *   <li>{@link #selectPendingTasks} — 按类型查询待处理任务（Worker 拉取）</li>
 *   <li>{@link #countActiveByTenant} — 统计租户下活跃任务数（配额检查）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTask> {

  /**
   * 查询已过期的 RUNNING 任务（expire_at &lt; 当前时间）。
   *
   * <p>用于定时任务回收超时任务，重新置为 PENDING 状态。
   *
   * @param now 当前时间
   * @return 过期任务列表
   */
  @Select("SELECT * FROM ydsz_agt_async_task WHERE status = 'RUNNING' AND expire_at < #{now}")
  List<AsyncTask> selectExpiredTasks(@Param("now") LocalDateTime now);

  /**
   * 按类型查询 PENDING 任务，按创建时间正序（优先分配最早创建的任务）。
   *
   * @param taskType 任务类型（null 表示不过滤）
   * @param limit    最多返回条数
   * @return 待处理任务列表
   */
  @Select("<script>"
      + "SELECT * FROM ydsz_agt_async_task WHERE status = 'PENDING' "
      + "<if test='taskType != null'>AND task_type = #{taskType} </if>"
      + "ORDER BY created_at ASC LIMIT #{limit}"
      + "</script>")
  List<AsyncTask> selectPendingTasks(@Param("taskType") String taskType, @Param("limit") int limit);

  /**
   * 统计租户下活跃（非终态）任务数。
   *
   * <p>用于配额检查，防止单一租户提交过多任务。
   *
   * @param tenantCode 租户编码
   * @param statuses   终态状态列表（NOT IN 过滤）
   * @return 活跃任务数
   */
  @Select("<script>"
      + "SELECT COUNT(*) FROM ydsz_agt_async_task "
      + "WHERE tenant_code = #{tenantCode} AND status IN "
      + "<foreach collection='activeStatuses' item='s' open='(' separator=',' close=')'>"
      + "#{s}"
      + "</foreach>"
      + "</script>")
  int countActiveByTenant(@Param("tenantCode") String tenantCode,
      @Param("activeStatuses") List<String> statuses);

  /**
   * 原子认领任务（乐观锁：UPDATE ... WHERE id=? AND status='PENDING'）。
   *
   * <p>利用数据库行级锁保证并发安全：多个 Worker 同时认领同一任务时，
   * 只有一个 UPDATE 影响行数 > 0，其他 Worker 影响行数为 0（竞争失败）。
   *
   * @param id        任务 ID
   * @param workerId  Worker 节点标识
   * @param startTime 开始时间
   * @return 影响行数（1=认领成功，0=已被其他 Worker 认领）
   */
  @Update("UPDATE ydsz_agt_async_task "
      + "SET status = 'RUNNING', worker_id = #{workerId}, started_at = #{startTime}, "
      + "updated_at = CURRENT_TIMESTAMP "
      + "WHERE id = #{id} AND status = 'PENDING'")
  int claimTask(@Param("id") Long id, @Param("workerId") String workerId,
      @Param("startTime") LocalDateTime startTime);

  /**
   * 释放超时任务：将 RUNNING 且已过期（expire_at &lt; 当前时间）的任务重置为 PENDING。
   *
   * @param now 当前时间
   * @return 释放的任务数
   */
  @Update("UPDATE ydsz_agt_async_task "
      + "SET status = 'PENDING', worker_id = NULL, progress_percent = 0, started_at = NULL, "
      + "updated_at = CURRENT_TIMESTAMP "
      + "WHERE status = 'RUNNING' AND expire_at < #{now}")
  int releaseTimeoutTasks(@Param("now") LocalDateTime now);

  /**
   * 更新任务进度和 Worker 心跳时间。
   *
   * @param id       任务 ID
   * @param percent  进度百分比
   * @param workerId Worker 标识
   * @return 影响行数
   */
  @Update("UPDATE ydsz_agt_async_task "
      + "SET progress_percent = #{percent}, updated_at = CURRENT_TIMESTAMP "
      + "WHERE id = #{id} AND worker_id = #{workerId} AND status = 'RUNNING'")
  int updateTaskProgress(@Param("id") Long id, @Param("percent") int percent,
      @Param("workerId") String workerId);
}
