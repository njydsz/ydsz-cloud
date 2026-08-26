package com.njydsz.workflow.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.dto.FlowAttachmentPreviewVO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.server.service.FlowAttachmentService;

/**
 * 审批附件控制器（P1-6 拆分自 FlowTaskController，路由不变）。
 *
 * <p>提供任务/实例附件查询、逻辑删除与在线预览能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
public class FlowAttachmentController {

  /** 审批附件服务 */
  private final FlowAttachmentService attachmentService;

  /**
   * 查询任务附件。
   *
   * @param taskId 任务 ID
   * @return 附件列表
   */
  @GetMapping("/attachment/task/{taskId}")
  @Operation(summary = "查询任务附件")
  public YdszResponse<List<FlowAttachmentVO>> listByTask(@PathVariable String taskId) {
    return YdszResponse.success(attachmentService.listByTask(taskId));
  }

  /**
   * 查询实例附件。
   *
   * @param instanceId 流程实例 ID
   * @return 附件列表
   */
  @GetMapping("/attachment/instance/{instanceId}")
  @Operation(summary = "查询实例附件")
  public YdszResponse<List<FlowAttachmentVO>> listByInstance(@PathVariable String instanceId) {
    return YdszResponse.success(attachmentService.listByInstance(instanceId));
  }

  /**
   * 删除附件（逻辑删除）。
   *
   * @param attachmentId 附件 ID
   * @param operatorId 操作人 ID（用于审计日志）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:attachment:delete", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowAttachment.delete", threshold = 50)
  @DeleteMapping("/attachment/{attachmentId}")
  @Audit(
      module = "流程附件",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  @Operation(summary = "删除附件（逻辑删除）")
  public YdszResponse<Void> delete(
      @PathVariable String attachmentId, @RequestParam String operatorId) {
    attachmentService.delete(attachmentId, operatorId);
    return YdszResponse.success();
  }

  /**
   * P2-3: 附件在线预览 — 根据文件类型返回预览策略与预览 URL。
   *
   * @param attachmentId 附件 ID
   * @return 统一响应结果，包含预览 VO
   */
  @GetMapping("/attachment/{attachmentId}/preview")
  @Operation(summary = "附件在线预览（根据文件类型返回预览策略）")
  public YdszResponse<FlowAttachmentPreviewVO> preview(@PathVariable String attachmentId) {
    return YdszResponse.success(attachmentService.previewAttachment(attachmentId));
  }
}
