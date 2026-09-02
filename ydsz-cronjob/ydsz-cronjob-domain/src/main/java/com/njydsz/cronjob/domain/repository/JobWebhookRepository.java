package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
 * @since 26.09.01
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

  // ===== Web 层 CRUD 方法（Controller 停止 Mapper 直注） =====

  /**
   * 新增 WebHook 订阅。
   *
   * @param vo Webhook VO
   * @return 新订阅 ID
   */
  String create(JobWebhookVO vo);

  /**
   * 更新 WebHook 订阅（按 ID 全字段更新）。
   *
   * @param vo Webhook VO（必须含 id）
   */
  void update(JobWebhookVO vo);

  /**
   * 逻辑删除 WebHook 订阅。
   *
   * @param id Webhook ID
   * @param updatedAt 更新时间
   */
  void deleteById(String id, LocalDateTime updatedAt);

  /**
   * 根据 ID 查询 WebHook 详情。
   *
   * @param id Webhook ID
   * @return Webhook VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobWebhookVO> findById(String id);

  /**
   * 分页查询 WebHook 订阅列表。
   *
   * <p>仅查询 {@code deleted=0} 的订阅，按 created_at 倒序。
   *
   * @param pageNum 页码（从 1 开始）
   * @param size 每页条数
   * @param eventType 事件类型过滤（可为 null 表示不限）
   * @param jobKey 任务 KEY 过滤（可为 null 表示不限）
   * @return 分页结果（records=VO列表, total=总条数）
   */
  JobRepository.PageResult<JobWebhookVO> pageBy(int pageNum, int size, String eventType, String jobKey);
}
