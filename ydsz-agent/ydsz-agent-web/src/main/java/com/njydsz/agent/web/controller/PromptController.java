package com.njydsz.agent.web.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.server.prompt.PromptEvaluationService;
import com.njydsz.agent.server.prompt.PromptEvaluationService.PromptComparisonResult;
import com.njydsz.agent.server.prompt.PromptEvaluationService.PromptEvaluationResult;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;

/**
 * Prompt 模板评估 REST API Controller。
 *
 * <p>提供 Prompt 模板的试运行与 A/B 对比评估能力，用于 Prompt 上线前的效果验证与版本横向对比。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/prompt")
@Tag(name = "Prompt 评估", description = "Prompt 模板试运行与对比评估")
public class PromptController {

  private final PromptEvaluationService evaluationService;

  public PromptController(PromptEvaluationService evaluationService) {
    this.evaluationService = evaluationService;
  }

  /**
   * 评估单个 Prompt 模板。
   *
   * <p>将模板渲染后发送到 LLM，返回延迟、Token 用量、成本估算等指标。
   *
   * @param request 评估请求
   * @return 评估结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "Prompt管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'evaluate'")
  @RateLimit(resource = "agent.prompt.evaluate", threshold = 20)
  @PostMapping("/evaluate")
  @Operation(summary = "评估 Prompt 模板", description = "渲染模板并发送到 LLM，返回性能指标")
  public YdszResponse<PromptEvaluationResult> evaluate(@Valid @RequestBody EvaluateRequest request) {
    log.info("[Prompt-API] 评估请求: template={}", request.templateCode());
    PromptEvaluationResult result =
        evaluationService.evaluate(
            request.templateCode(),
            request.variables(),
            request.userMessage(),
            request.model());
    return YdszResponse.success(result);
  }

  /**
   * 对比评估两个 Prompt 模板。
   *
   * <p>使用相同输入和模型分别执行两个模板，返回并排指标对比。
   *
   * @param request 对比请求
   * @return 对比结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "Prompt管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'compare'")
  @RateLimit(resource = "agent.prompt.evaluate", threshold = 10)
  @PostMapping("/compare")
  @Operation(summary = "对比评估两个 Prompt 模板", description = "相同输入下对比两个模板的性能指标")
  public YdszResponse<PromptComparisonResult> compare(@Valid @RequestBody CompareRequest request) {
    log.info("[Prompt-API] 对比请求: A={}, B={}", request.templateCodeA(), request.templateCodeB());
    PromptComparisonResult result =
        evaluationService.compare(
            request.templateCodeA(),
            request.templateCodeB(),
            request.variables(),
            request.userMessage(),
            request.model());
    return YdszResponse.success(result);
  }

  /** 单次评估请求 */
  public record EvaluateRequest(
      @NotBlank(message = "模板编码不能为空") String templateCode,
      Map<String, Object> variables,
      String userMessage,
      String model) {}

  /** 对比评估请求 */
  public record CompareRequest(
      @NotBlank(message = "模板 A 编码不能为空") String templateCodeA,
      @NotBlank(message = "模板 B 编码不能为空") String templateCodeB,
      Map<String, Object> variables,
      String userMessage,
      String model) {}
}
