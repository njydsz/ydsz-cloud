package com.njydsz.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.domain.entity.job.JobHistory;

/**
 * 任务变更历史 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_history</code>。
 *
 * <p>变更历史追踪任务配置的修改（CRON/参数/重试策略），用于审计与回滚。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_job_id — 任务维度查询索引
 *   <li>idx_changed_at — 变更时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see com.njydsz.cronjob.domain.entity.job.JobHistory 变更历史实体
 * @see com.njydsz.cronjob.server.service.JobHistoryService 变更历史 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobHistoryMapper extends BaseMapper<JobHistory> {

  /**
   * 查询指定任务的所有历史版本（按版本号降序）。
   *
   * @param jobId 任务 ID
   * @return 历史版本列表（版本号降序）；无记录时返回空列表
   */
  @Select(
      "SELECT id, job_id, version, snapshot, change_type, before_snapshot, change_remark, "
          + "       job_name, job_key, handler, cron_expression, params_json, remark, "
          + "       changed_by, changed_at, deleted "
          + "FROM ydsz_job_history "
          + "WHERE job_id = #{jobId} AND deleted = 0 "
          + "ORDER BY version DESC")
  List<JobHistory> selectByJobIdOrderByVersionDesc(@Param("jobId") String jobId);

  /**
   * 查询指定任务的指定历史版本。
   *
   * @param jobId 任务 ID
   * @param version 版本号
   * @return 历史版本记录；不存在时返回 null
   */
  @Select(
      "SELECT id, job_id, version, snapshot, change_type, before_snapshot, change_remark, "
          + "       job_name, job_key, handler, cron_expression, params_json, remark, "
          + "       changed_by, changed_at, deleted "
          + "FROM ydsz_job_history "
          + "WHERE job_id = #{jobId} AND version = #{version} AND deleted = 0")
  JobHistory selectByVersion(@Param("jobId") String jobId, @Param("version") Integer version);

  /**
   * 批量删除过期历史记录（基于 ctid 物理地址，避免回表）。
   *
   * <p>PostgreSQL 特有优化：使用 ctid = ANY(ARRAY(...)) 替代 id IN (SELECT id ...)，
   * 直接通过物理行地址定位数据页，避免二次索引扫描，大表删除性能提升 3-5 倍。
   *
   * @param before 过期分界时间（删除 changed_at 早于该时间的历史记录）
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  @Delete(
      "DELETE FROM ydsz_job_history "
          + "WHERE ctid = ANY(ARRAY("
          + "  SELECT ctid FROM ydsz_job_history "
          + "  WHERE changed_at < #{before} "
          + "  LIMIT #{limit}"
          + "))")
  int cleanExpiredLogs(@Param("before") LocalDateTime before, @Param("limit") int limit);

  /**
   * 软删除过期历史记录（TTL 归档）。
   *
   * <p>将 changed_at 早于阈值且未删除的记录标记 is_deleted = true，而非物理删除。
   * 使用 ctid 批量定位，每批最多处理 limit 条，避免大事务锁表。
   *
   * @param before 过期分界时间
   * @param limit 单批最多处理条数
   * @return 实际软删除条数
   */
  @Update(
      "UPDATE ydsz_job_history SET is_deleted = true, updated_at = NOW() "
          + "WHERE ctid = ANY(ARRAY("
          + "  SELECT ctid FROM ydsz_job_history "
          + "  WHERE changed_at < #{before} AND is_deleted = false "
          + "  LIMIT #{limit}"
          + "))")
  int softDeleteExpired(@Param("before") LocalDateTime before, @Param("limit") int limit);

  /**
   * 统计过期未删除的历史记录条数（dryRun 预览）。
   *
   * @param before 过期分界时间
   * @return 过期且未删除的记录条数
   */
  @Select(
      "SELECT COUNT(*) FROM ydsz_job_history "
          + "WHERE changed_at < #{before} AND is_deleted = false")
  int countExpired(@Param("before") LocalDateTime before);
}
