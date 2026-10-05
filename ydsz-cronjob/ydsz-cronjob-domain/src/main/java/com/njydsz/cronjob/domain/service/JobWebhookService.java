package com.njydsz.cronjob.domain.service;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;

/**
 * WebHook 事件订阅 Service 接口（domain 层）。
 *
 * <p>封装 WebHook 订阅的 CRUD + 测试推送能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface JobWebhookService {

  /**
   * 新增 WebHook 订阅。
   *
   * @param dto WebHook 配置 DTO
   * @return 新订阅 ID
   */
  String create(JobWebhookPostDTO dto);

  /**
   * 更新 WebHook 订阅。
   *
   * @param dto WebHook 更新 DTO
   */
  void update(JobWebhookPutDTO dto);

  /**
   * 删除 WebHook 订阅（逻辑删除）。
   *
   * @param id WebHook ID
   */
  void delete(String id);

  /**
   * 分页查询 WebHook 订阅列表。
   *
   * @param pageNum 页码
   * @param size 每页条数
   * @param eventType 事件类型过滤（可选）
   * @param jobKey 任务 KEY 过滤（可选）
   * @return 分页结果
   */
  PageResponse<List<JobWebhookVO>> page(int pageNum, int size, String eventType, String jobKey);

  /**
   * 查询 WebHook 详情。
   *
   * @param id WebHook ID
   * @return WebHook VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobWebhookVO> findById(String id);

  /**
   * 测试 WebHook 推送。
   *
   * @param id WebHook ID
   * @return true=推送成功；false=推送失败
   */
  boolean testWebhook(String id);
}
