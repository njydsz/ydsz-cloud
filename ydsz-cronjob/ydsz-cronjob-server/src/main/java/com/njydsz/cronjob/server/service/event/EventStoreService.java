package com.njydsz.cronjob.server.service.event;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.event.JobEvent;

/**
 * 事件存储服务接口（P3-1 Event Sourcing）。
 *
 * <p>提供事件追加和查询能力，供业务层记录领域事件、供查询层读取事件流。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface EventStoreService {

  /**
   * 记录任务创建事件。
   *
   * @param jobId 任务 ID
   * @param payload 事件负载（任务创建时的完整信息 JSON）
   * @param operator 操作人
   */
  void recordJobCreated(String jobId, String payload, String operator);

  /**
   * 记录任务更新事件。
   *
   * @param jobId 任务 ID
   * @param payload 事件负载（更新内容 JSON）
   * @param operator 操作人
   */
  void recordJobUpdated(String jobId, String payload, String operator);

  /**
   * 记录任务状态变更事件。
   *
   * @param jobId 任务 ID
   * @param fromStatus 原状态
   * @param toStatus 新状态
   * @param operator 操作人
   */
  void recordJobStatusChanged(String jobId, String fromStatus, String toStatus, String operator);

  /**
   * 记录任务触发事件。
   *
   * @param jobId 任务 ID
   * @param triggerType 触发类型（CRON / MANUAL / API / DEPENDENT）
   * @param operator 操作人（MANUAL 触发时为操作人，其他为 "system"）
   */
  void recordJobTriggered(String jobId, String triggerType, String operator);

  /**
   * 记录任务删除事件。
   *
   * @param jobId 任务 ID
   * @param operator 操作人
   */
  void recordJobDeleted(String jobId, String operator);

  /**
   * 记录任务集群漂移事件。
   *
   * @param jobId 任务 ID
   * @param fromCluster 源集群
   * @param toCluster 目标集群
   * @param operator 操作人
   */
  void recordJobMigrated(
      String jobId, String fromCluster, String toCluster, String operator);

  /**
   * 查询任务的事件流（按时间升序）。
   *
   * @param jobId 任务 ID
   * @return 事件列表
   */
  List<JobEvent> getJobEventStream(String jobId);

  /**
   * 按事件类型分页查询。
   *
   * @param eventType 事件类型（null 表示全部）
   * @param pageNum 页码
   * @param size 每页条数
   * @return 分页结果
   */
  PageResponse<List<JobEvent>> pageByType(String eventType, int pageNum, int size);
}
