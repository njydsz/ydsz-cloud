package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.constants.CronjobConstants;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.domain.enums.CronjobExceptionCode;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.JobWebhookRepository;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.server.core.dispatch.WebhookEventDispatcher;

/**
 * WebHook 事件订阅管理 Controller（P3-13）。
 *
 * <p>提供 WebHook 订阅的增删改查接口，支持按事件类型和任务 KEY 订阅。 任务执行过程中产生的关键事件（成功/失败/超时/开始/结束）会通过 {@code
 * WebhookEventDispatcher} 异步推送到已注册的 WebHook。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>CRUD：创建 / 更新 / 删除 / 详情 / 分页查询
 *   <li>过滤：按 eventType 和 jobKey 组合过滤
 *   <li>测试：{@link #testWebhook} 主动发送测试事件
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "WebHook 事件订阅", description = "订阅 CRUD、过滤查询、测试推送")
@Slf4j
@RequestMapping("/api/v1/cronjob/webhook")
@RequiredArgsConstructor
public class JobWebhookController {

  /** WebHook 订阅 Repository（DDD 分层：Controller 通过 Repository 接口访问） */
  private final JobWebhookRepository webhookRepository;

  /** P0-F3: WebHook 事件分发器（发送测试事件） */
  private final WebhookEventDispatcher webhookEventDispatcher;

  /**
   * 新增 WebHook 订阅。
   *
   * <p>订阅创建后立即生效（{@code status=ACTIVE}）。默认 HTTP 方法 POST，可通过入参覆盖。 订阅的 eventType 支持通配（{@code *}
   * 表示订阅所有事件）。
   *
   * @param dto WebHook 配置（url/eventType/jobKey/httpMethod/headers/secret）
   * @return 新订阅 ID
   */
  @Operation(summary = "新增 WebHook 订阅")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobWebhookController:create:lock", ttlSeconds = 5)
  @Audit(
      module = "WebHook",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'create'")
  @RateLimit(resource = "cronjob.jobwebhook.create", threshold = 50)
  @PostMapping
  public YdszResponse<String> create(@RequestBody JobWebhookPostDTO dto) {
    // 1. DTO → VO（经 Entity 转换，Repository 层接受 VO）
    JobWebhookVO vo = CronjobConverter.INSTANT.entityToVO(CronjobConverter.INSTANT.postDtoToEntity(dto));
    vo.setWebhookStatus(CronjobConstants.WEBHOOK_STATUS_ACTIVE);
    vo.setCreatedAt(LocalDateTime.now());
    vo.setUpdatedAt(LocalDateTime.now());
    if (vo.getHttpMethod() == null || vo.getHttpMethod().isBlank()) {
      vo.setHttpMethod(CronjobConstants.HTTP_METHOD_POST);
    }
    // 2. 通过 Repository 新增
    String newId = webhookRepository.create(vo);
    return YdszResponse.success(newId);
  }

  /**
   * 更新 WebHook 订阅。
   *
   * <p>修改 url/headers/secret 等配置。eventType/jobKey 不允许变更（应删除重建）。
   *
   * @param dto WebHook 更新请求体（必须含 id）
   * @return 统一响应结果
   */
  @Operation(summary = "更新 WebHook 订阅")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobWebhookController:update:lock", ttlSeconds = 5)
  @Audit(
      module = "WebHook",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'update'")
  @RateLimit(resource = "cronjob.jobwebhook.update", threshold = 50)
  @PutMapping
  public YdszResponse<Void> update(@RequestBody JobWebhookPutDTO dto) {
    // DTO → VO（经 Entity 转换）
    JobWebhookVO vo = CronjobConverter.INSTANT.entityToVO(CronjobConverter.INSTANT.putDtoToEntity(dto));
    vo.setUpdatedAt(LocalDateTime.now());
    webhookRepository.update(vo);
    return YdszResponse.success();
  }

  /**
   * 删除 WebHook 订阅（逻辑删除）。
   *
   * <p>将 {@code deleted=1} 标记为软删除，不再接收事件推送。 历史事件记录保留，便于审计追溯。
   *
   * @param id WebHook ID
   * @return 统一响应结果
   */
  @Operation(summary = "删除 WebHook 订阅")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobWebhookController:delete:lock", ttlSeconds = 5)
  @Audit(
      module = "WebHook",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  @RateLimit(resource = "cronjob.jobwebhook.delete", threshold = 50)
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable String id) {
    webhookRepository.deleteById(id, LocalDateTime.now());
    return YdszResponse.success();
  }

  /**
   * 分页查询 WebHook 订阅列表。
   *
   * <p>按 created_at 倒序排列。eventType/jobKey 为可选过滤条件，二者可组合（AND 关系）。
   *
   * @param page 页码（默认 1）
   * @param size 每页条数（默认 20）
   * @param eventType 事件类型过滤（可选）
   * @param jobKey 任务 KEY 过滤（可选）
   * @return WebHook 分页数据
   */
  @Operation(summary = "分页查询 WebHook 订阅")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<JobWebhookVO>>> page(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String jobKey) {
    // 通过 Repository 分页查询（封装了 MyBatis-Plus Page 和 Entity→VO 转换）
    JobRepository.PageResult<JobWebhookVO> result = webhookRepository.pageBy(pageNum, size, eventType, jobKey);
    return YdszResponse.success(PageResponse.success((long) pageNum, (long) size, result.getTotal(), result.getRecords()));
  }

  /**
   * 查询 WebHook 详情。
   *
   * @param id WebHook ID
   * @return WebHook 配置详情 VO
   */
  @Operation(summary = "查询 WebHook 详情")
  @GetMapping("/{id}")
  public YdszResponse<JobWebhookVO> getById(@PathVariable String id) {
    return webhookRepository.findById(id)
        .map(YdszResponse::success)
        .orElse(YdszResponse.error(CronjobExceptionCode.WEBHOOK_NOT_FOUND, "WebHook not found"));
  }

  /**
   * 测试 WebHook 推送（发送测试事件）。
   *
   * <p>P0-F3: 主动发送一个 {@code TEST_WEBHOOK} 类型的合成事件，用于验证 WebHook 配置正确性。
   * 原实现方法体为空（仅 return success），现通过 {@link WebhookEventDispatcher#sendTest} 真实推送。
   * 同步执行并返回推送结果：失败时返回业务错误，方便前端提示。
   *
   * @param id WebHook ID
   * @return 统一响应结果（success=推送成功；error=推送失败）
   */
  @Operation(summary = "测试 WebHook 推送")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobWebhookController:testWebhook:lock", ttlSeconds = 5)
  @Audit(
      module = "WebHook",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'testWebhook'")
  @RateLimit(resource = "cronjob.jobwebhook.testWebhook", threshold = 50)
  @PostMapping("/{id}/test")
  public YdszResponse<Void> testWebhook(@PathVariable String id) {
    // 通过 Repository 查询 Webhook
    JobWebhookVO webhookVO = webhookRepository.findById(id)
        .orElse(null);
    if (webhookVO == null) {
      return YdszResponse.error(CronjobExceptionCode.WEBHOOK_NOT_FOUND, "WebHook not found");
    }
    // P0-F3: 通过 WebhookEventDispatcher 真实发送测试事件（含重试）
    var webhook = CronjobConverter.INSTANT.voToEntity(webhookVO);
    boolean sent = webhookEventDispatcher.sendTest(webhook);
    if (!sent) {
      return YdszResponse.error(
          CronjobExceptionCode.WEBHOOK_SEND_FAILED, "WebHook 测试推送失败，请检查 URL / 网络 / 签名配置");
    }
    return YdszResponse.success();
  }
}
