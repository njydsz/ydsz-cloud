package com.njydsz.cronjob.domain.repository;

import java.util.List;

import com.njydsz.cronjob.domain.vo.JobWebhookVO;

/**
 * 任务 Webhook Repository（domain 层契约）。
 *
 * <p>定义任务 Webhook 事件订阅的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobWebhookVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobWebhookRepository {

  /**
   * 根据事件类型查询活跃的 Webhook 列表。
   *
   * @param eventType 事件类型
   * @return 活跃的 Webhook VO 列表
   */
  List<JobWebhookVO> findActiveByEventType(String eventType);

  /**
   * 根据事件类型和任务 ID 查询活跃的 Webhook 列表。
   *
   * @param eventType 事件类型
   * @param jobId 任务 ID
   * @return 活跃的 Webhook VO 列表
   */
  List<JobWebhookVO> findActiveByEventAndJob(String eventType, String jobId);
}
