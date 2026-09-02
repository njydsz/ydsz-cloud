package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobWebhookRetryVO;

/**
 * WebHook 重试补偿 Repository（domain 层契约，P1-3 Webhook 投递保障）。
 *
 * <p>定义 Webhook 失败重试补偿的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobWebhookRetryVO}），非 DTO / infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface WebhookRetryRepository {

  /**
   * 创建重试记录。
   *
   * @param vo 重试记录 VO
   * @return 新记录 ID
   */
  String create(JobWebhookRetryVO vo);

  /**
   * 查询待重试的记录。
   *
   * @param now 当前时间
   * @param limit 批量大小
   * @return 待重试记录列表
   */
  List<JobWebhookRetryVO> findPendingRetries(LocalDateTime now, int limit);

  /**
   * 更新重试状态（递增重试次数、设置下次重试时间）。
   *
   * @param id 记录 ID
   * @param retryCount 当前重试次数
   * @param nextRetryTime 下次重试时间
   * @param lastError 最后错误信息（可为 null）
   */
  void updateForRetry(String id, int retryCount, LocalDateTime nextRetryTime, String lastError);

  /**
   * 标记重试成功。
   *
   * @param id 记录 ID
   * @param successTime 成功时间
   */
  void markSuccess(String id, LocalDateTime successTime);

  /**
   * 标记死信（超出最大重试次数）。
   *
   * @param id 记录 ID
   * @param deadTime 标记时间
   * @param reason 死信原因
   */
  void markDead(String id, LocalDateTime deadTime, String reason);

  /**
   * 统计待重试记录数。
   *
   * @return 待重试记录数
   */
  long countPending();

  /**
   * 统计死信记录数。
   *
   * @return 死信记录数
   */
  long countDead();
}
