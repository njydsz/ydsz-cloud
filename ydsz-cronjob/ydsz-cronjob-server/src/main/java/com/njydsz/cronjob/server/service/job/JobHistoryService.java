package com.njydsz.cronjob.server.service.job;

import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.vo.JobHistoryVO;
import com.njydsz.cronjob.infra.entity.job.Job;

/**
 * 任务配置历史版本服务（P1-6 任务版本管理）。
 *
 * <p>提供任务配置的版本管理能力：保存历史快照、查询版本列表、查询指定版本、 一键回滚到指定版本、对比两个版本的差异。每次任务更新前自动调用 {@link #saveHistory(Job,
 * String)} 保存当前配置的完整 JSON 快照， 便于审计与回滚。
 *
 * <p>回滚操作会基于历史快照恢复配置字段，同时保留当前任务的统计字段 （触发次数、成功/失败次数等），并产生新的历史版本。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobHistoryService {

  /**
   * 保存历史版本（将 Job 序列化为 JSON 存入 snapshot）。
   *
   * <p>版本号取自 {@code job.version}，冗余字段（jobName/jobKey/handler 等） 便于版本列表快速展示而无需反序列化 snapshot。
   *
   * @param job 任务定义（更新前的当前状态）
   * @param changedBy 修改人 ID
   * @return 新创建的历史版本 VO
   */
  JobHistoryVO saveHistory(Job job, String changedBy);

  /**
   * 获取指定任务的版本列表（按版本号降序）。
   *
   * @param jobId 任务 ID
   * @return 历史版本列表；无记录时返回空列表
   */
  List<JobHistoryVO> listVersions(String jobId);

  /**
   * 获取指定任务的指定历史版本详情。
   *
   * @param jobId 任务 ID
   * @param version 版本号
   * @return 历史版本 VO；不存在时返回 null
   */
  JobHistoryVO getVersion(String jobId, Integer version);

  /**
   * 回滚到指定版本。
   *
   * <p>从历史快照中恢复配置字段，保留当前任务的 id/jobKey/tenantId/统计字段/createdAt， 更新 version = max(历史版本号) + 1，调用
   * jobMapper.updateById 持久化， 并保存新的历史版本。
   *
   * @param jobId 任务 ID
   * @param version 目标版本号
   * @return 回滚后的 Job
   */
  Job rollback(String jobId, Integer version);

  /**
   * 对比两个版本的差异。
   *
   * @param jobId 任务 ID
   * @param version1 旧版本号
   * @param version2 新版本号
   * @return 差异字段列表，每个元素包含 field/oldValue/newValue
   */
  List<Map<String, Object>> compareVersions(String jobId, Integer version1, Integer version2);

  /**
   * 记录版本变更快照。
   *
   * <p>统一版本管理入口，同时保存变更前/变更后快照，支持 CREATE / UPDATE / DELETE 三种变更类型。 内部将 before/after 序列化为 JSON 存入
   * {@code before_snapshot} 和 {@code snapshot} 字段。
   *
   * @param beforeJob 变更前的任务定义（CREATE 时为 null）
   * @param afterJob 变更后的任务定义（DELETE 时为 null）
   * @param changeType 变更类型: CREATE / UPDATE / DELETE
   * @param changedBy 变更人
   * @param changeRemark 变更说明
   */
  void recordVersionChange(
      Job beforeJob, Job afterJob, String changeType, String changedBy, String changeRemark);
}
