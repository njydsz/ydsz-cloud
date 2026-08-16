package com.njydsz.workflow.web.controller.integration;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.dto.FlowAttachmentPreviewVO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.server.service.FlowAttachmentService;

/**
 * 审批附件 Controller
 *
 * <p>P1-6 (GAP-51): 审批附件的查询与删除接口。 文件二进制上传由统一文件服务（OSS/MinIO）处理，此处仅管理附件元数据。
 *
 * <p>P2-3: 新增在线预览接口，根据文件类型返回预览策略与预览 URL。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/engine/attachment/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>按任务查询</b>：{@code GET /attachment/task/{taskId}} — 当前任务上传的附件
 *   <li><b>按实例查询</b>：{@code GET /attachment/instance/{instanceId}} — 整个流程实例的全部附件
 *   <li><b>删除附件</b>：{@code DELETE /attachment/{id}} — 仅元数据逻辑删除，不联动文件存储
 *   <li><b>在线预览</b>：{@code GET /attachment/{id}/preview} — 根据文件类型返回预览策略与 URL
 * </ul>
 *
 * <p><b>附件生命周期：</b>
 *
 * <ol>
 *   <li>前端通过统一文件服务上传文件二进制（OSS / MinIO / 本地）
 *   <li>前端调用 {@code ProjectFileController} 等业务文件服务记录文件元数据
 *   <li>前端将附件 ID 关联到具体任务（{@code FlowTaskService.attachFile}）
 *   <li>本 Controller 提供查询、删除、预览能力
 * </ol>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重（5s）
 *   <li>写接口启用 {@link RateLimit} 限流 50 QPS
 *   <li>删除为逻辑删除（{@code deleted=1}），保留历史可追溯
 *   <li>附件元数据隔离多租户，跨租户不可见
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.FlowAttachmentService 附件 Service
 * @see com.njydsz.common.file.storage.IFileStorageProvider 统一文件存储服务
 */
@Slf4j
@RestController
@Tag(name = "workflow-attachment", description = "工作流审批附件接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
public class FlowAttachmentController {

  /** 审批附件服务，负责附件元数据管理与在线预览 */
  private final FlowAttachmentService attachmentService;

  /**
   * 查询任务附件。
   *
   * @param taskId 任务 ID
   * @return 附件列表
   */
  @GetMapping("/attachment/task/{taskId}")
  public BaseResponse<List<FlowAttachmentVO>> listByTask(@PathVariable String taskId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowAttachmentListToVO(attachmentService.listByTask(taskId)));
  }

  /**
   * 查询实例附件。
   *
   * @param instanceId 流程实例 ID
   * @return 附件列表
   */
  @GetMapping("/attachment/instance/{instanceId}")
  public BaseResponse<List<FlowAttachmentVO>> listByInstance(@PathVariable String instanceId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowAttachmentListToVO(
            attachmentService.listByInstance(instanceId)));
  }

  /**
   * 删除附件（逻辑删除）
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p><b>逻辑删除</b>：仅清除元数据记录（{@code deleted=1}），不联动清除文件存储中的二进制， 避免误删导致的历史数据丢失。彻底清理文件由定期任务统一执行。
   *
   * <p>权限校验：仅附件上传人或管理员可删除（由 Service 层校验）。
   *
   * @param attachmentId 附件 ID
   * @param operatorId 操作人 ID（用于审计日志）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowAttachmentController:delete:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowattachment.delete", threshold = 50)
  @DeleteMapping("/attachment/{attachmentId}")
  @Audit(
      module = "流程附件",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  public BaseResponse<Void> delete(
      @PathVariable String attachmentId, @RequestParam String operatorId) {
    attachmentService.delete(attachmentId, operatorId);
    return BaseResponse.success();
  }

  /**
   * P2-3: 附件在线预览 — 根据文件类型返回预览策略与预览 URL。
   *
   * <p>前端根据 {@code previewType} 选择渲染方式：
   *
   * <ul>
   *   <li>IMAGE → {@code <img src=previewUrl>}
   *   <li>PDF → {@code <iframe src=previewUrl>} 或 PDF.js
   *   <li>VIDEO → {@code <video src=previewUrl>}
   *   <li>TEXT → fetch 后 {@code <pre>} 渲染
   *   <li>OFFICE → {@code <iframe src=previewUrl>}（外部预览服务）
   *   <li>UNSUPPORTED → 引导下载（downloadUrl）
   * </ul>
   *
   * @param attachmentId 附件 ID
   * @return 统一响应结果，包含预览 VO
   */
  @GetMapping("/attachment/{attachmentId}/preview")
  @Operation(summary = "附件在线预览（根据文件类型返回预览策略）")
  public BaseResponse<FlowAttachmentPreviewVO> preview(@PathVariable String attachmentId) {
    return BaseResponse.success(attachmentService.previewAttachment(attachmentId));
  }
}
