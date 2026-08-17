package com.njydsz.cronjob.infra.repository;

import java.util.List;

import com.njydsz.cronjob.domain.entity.job.JobWebhook;

/**
 * 任务 Webhook Repository。
 *
 * <p>封装 {@code ydsz_job_webhook} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobWebhookRepository {

  /**
   * 根据事件类型查询活跃的 Webhook 列表。
   *
   * @param eventType 事件类型
   * @return 活跃的 Webhook 列表
   */
  List<JobWebhook> selectActiveByEventType(String eventType);

  /**
   * 根据事件类型和任务 ID 查询活跃的 Webhook 列表。
   *
   * @param eventType 事件类型
   * @param jobId 任务 ID
   * @return 活跃的 Webhook 列表
   */
  List<JobWebhook> selectActiveByEventAndJob(String eventType, String jobId);
}
