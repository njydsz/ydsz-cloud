package com.njydsz.nextwiki.web.controller.ai;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.TagDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.server.service.AiSummaryService;

/**
 * AI 能力 REST API Controller。
 *
 * <p>提供文件智能摘要等 AI 能力，预留接口供后续对接 LLM 服务。
 *
 * <ul>
 *   <li>{@code POST /ai/summary} - 生成文件内容摘要
 *   <li>{@code GET /ai/status} - 查询 AI 服务可用状态
 *   <li>{@code GET /ai/supported-types} - 查询支持的文件类型
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/ai")
@RequiredArgsConstructor
@TagDO(name = "AI 智能能力", description = "文件智能摘要、关键词提取等 AI 能力（预留接口）")
public class AiController {

  /** AI 摘要服务 */
  private final AiSummaryService aiSummaryService;

  /**
   * 生成文件内容摘要。
   *
   * <p>基于文件内容调用 LLM 生成智能摘要，支持简短/详细/关键点三种类型。
   *
   * @param request 摘要请求（fileNodeId / summaryType / maxLength）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为摘要内容
   */
  @Audit(
      module = "AI 能力",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'generateSummary'")
  @Idempotent(key = "ydsz:nextwiki:AiController:generateSummary:lock", ttlSeconds = 10)
  @PostMapping("/summary")
  @Operation(summary = "生成文件智能摘要")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_AI_SUMMARY)
  public BaseResponse<NextwikiDTOs.SummaryResult> generateSummary(
      @Valid @RequestBody NextwikiDTOs.GenerateSummaryRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    NextwikiDTOs.SummaryResult result =
        aiSummaryService.generateSummary(
            request.getFileNodeId(), request.getSummaryType(), request.getMaxLength());
    return BaseResponse.success(result);
  }

  /**
   * 查询 AI 服务可用状态。
   *
   * @return 可用状态信息
   */
  @GetMapping("/status")
  @Operation(summary = "查询 AI 服务状态")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_AI_STATUS)
  public BaseResponse<AiStatusResponse> getStatus() {
    AiStatusResponse response = new AiStatusResponse();
    response.setAvailable(aiSummaryService.isAvailable());
    response.setSupportedFileTypes(aiSummaryService.getSupportedFileTypes());
    return BaseResponse.success(response);
  }

  /** AI 服务状态响应。 */
  @lombok.Data
  public static class AiStatusResponse {
    /** 是否可用 */
    private boolean available;

    /** 支持的文件类型 */
    private List<String> supportedFileTypes;
  }
}
