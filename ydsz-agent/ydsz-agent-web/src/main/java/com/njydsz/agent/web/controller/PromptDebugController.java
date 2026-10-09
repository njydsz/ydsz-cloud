package com.njydsz.agent.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.server.chat.SseExecutor;
import com.njydsz.agent.server.chat.SseExecutor.SseChunk;
import com.njydsz.agent.server.prompt.PromptPlaygroundService;
import com.njydsz.agent.server.prompt.PromptPlaygroundService.PromptInvokeResult;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.socket.push.SsePushChannel;
import com.njydsz.common.socket.push.SsePushChannelFactory;

/**
 * Prompt Playground 交互调试 REST API Controller。
 *
 * <p>提供 Prompt 开发者的实时在线调试能力：
 *
 * <ul>
 *   <li>{@code POST /agent/prompt/playground/invoke} — 同步单次 LLM 调用，返回完整响应与性能指标</li>
 *   <li>{@code POST /agent/prompt/playground/stream} — 流式 LLM 调用（SSE 实时推送）</li>
 *   <li>{@code POST /agent/prompt/playground/compare} — 多模型对比，相同 Prompt 不同模型的响应差异</li>
 * </ul>
 *
 * <p>与 {@link PromptController} 的区别：本 Controller 接受原始 prompt text（不经模板渲染），
 * 且支持自定义 temperature / maxTokens 等推理参数，面向开发者实时调整参数验证效果。
 *
 * <h3>SSE 流式实现</h3>
 *
 * <ul>
 *   <li>使用 {@link SsePushChannelFactory} 创建 SSE 通道（统一心跳保活 + cleanup）</li>
 *   <li>使用虚拟线程承载 LLM 调用，节省线程资源</li>
 *   <li>客户端断开时通过 active 标志中断执行，节省 LLM Token</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see PromptController Prompt 模板评估接口
 * @see PromptPlaygroundService Prompt 交互评估服务
 */
@Slf4j
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/agent/prompt/playground")
@RequiredArgsConstructor
@Tag(name = "Prompt Playground", description = "Prompt 实时交互调试（invoke / stream / compare）")
public class PromptDebugController {

  /** SSE 通道工厂（统一 SSE 生命周期：心跳保活 + cleanup，来自 ydzs-common-socket） */
  private final SsePushChannelFactory ssePushChannelFactory;

  /** Prompt 交互评估服务 */
  private final PromptPlaygroundService playgroundService;

  /**
   * 同步单次 LLM 调用。
   *
   * <p>将 prompt 作为 system message、userMessage 作为 user message 发送到 LLM，
   * 返回完整响应与性能监控指标（延迟、Token 用量、成本估算）。
   *
   * <p>调用方需具有 DAG 执行权限（{@code AGENT_DAG_EXECUTE}），限流 20 QPS。
   *
   * @param request 调用请求体（prompt 必填；model / temperature / maxTokens 可选）
   * @return 统一响应结果，data 为 {@link PromptInvokeResult}（含内容 / 指标 / 时间戳）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "Prompt Playground",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'invoke'")
  @RateLimit(resource = "agent.prompt.playground.invoke", threshold = 20)
  @PostMapping("/invoke")
  @Operation(
      summary = "单次 LLM 调用",
      description = "将 prompt 作为 system message 发送到 LLM，返回完整响应与性能指标")
  public YdszResponse<PromptInvokeResult> invoke(@Valid @RequestBody PromptInvokeRequest request) {
    log.info("[PromptPlayground-API] invoke 请求: model={}, temp={}, maxTokens={}",
        request.model(), request.temperature(), request.maxTokens());
    PromptInvokeResult result =
        playgroundService.invoke(
            request.prompt(),
            request.userMessage(),
            request.model(),
            request.temperature(),
            request.maxTokens());
    return YdszResponse.success(result);
  }

  /**
   * 流式 LLM 调用（SSE 实时推送）。
   *
   * <p>基于 Server-Sent Events 逐 token 推送 LLM 响应内容，适合 Prompt 开发者实时观察生成过程。
   *
   * <p>事件类型：{@code chunk}（增量内容）/ {@code done}（正常结束）/ {@code error}（异常结束）。
   *
   * <p>调用方需具有 DAG 执行权限（{@code AGENT_DAG_EXECUTE}），限流 10 QPS。
   *
   * @param request 调用请求体（prompt 必填；model / temperature / maxTokens 可选）
   * @return SseEmitter（Spring MVC 的 SSE 句柄）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "Prompt Playground",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'invokeStream'")
  @RateLimit(resource = "agent.prompt.playground.stream", threshold = 10)
  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "流式 LLM 调用（SSE）",
      description = "逐 token 推送 LLM 响应，支持心跳保活和断连检测")
  public SseEmitter invokeStream(@Valid @RequestBody PromptInvokeRequest request) {
    log.info("[PromptPlayground-API] stream 请求: model={}, temp={}, maxTokens={}",
        request.model(), request.temperature(), request.maxTokens());
    SsePushChannel channel = ssePushChannelFactory.create();
    SseExecutor executor = new SseExecutor(channel);

    executor.execute(
        chunkConsumer ->
            playgroundService.invokeStream(
                request.prompt(),
                request.userMessage(),
                request.model(),
                request.temperature(),
                request.maxTokens(),
                chunk -> {
                  if (chunk.hasContent()) {
                    chunkConsumer.accept(
                        SseChunk.content(
                            chunk.getDeltaContent(),
                            chunk.getFinishReason(),
                            chunk.getDeltaToolCalls()));
                  } else if (chunk.isFinished()) {
                    chunkConsumer.accept(SseChunk.finish(chunk.getFinishReason()));
                  } else {
                    chunkConsumer.accept(
                        SseChunk.content(
                            chunk.getDeltaContent(),
                            chunk.getFinishReason(),
                            chunk.getDeltaToolCalls()));
                  }
                }));

    return channel.getEmitter();
  }

  /**
   * 多模型对比：使用相同 Prompt 分别调用不同 LLM 模型。
   *
   * <p>对每个模型执行同步 LLM 调用，收集延迟、Token、成本等指标并返回。
   * 单个模型失败时记录错误继续处理其他模型。
   *
   * <p>调用方需具有 DAG 执行权限（{@code AGENT_DAG_EXECUTE}），限流 5 QPS（涉及多次 LLM 调用）。
   *
   * @param request 对比请求体（prompt 必填，models 至少 1 个；temperature / maxTokens 可选）
   * @return 统一响应结果，data 为各模型的 {@link PromptInvokeResult} 列表（保持输入顺序）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "Prompt Playground",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'compare'")
  @RateLimit(resource = "agent.prompt.playground.compare", threshold = 5)
  @PostMapping("/compare")
  @Operation(
      summary = "多模型对比",
      description = "相同 Prompt 分别调用不同 LLM 模型，返回并排性能指标")
  public YdszResponse<List<PromptInvokeResult>> compare(
      @Valid @RequestBody PromptCompareRequest request) {
    log.info("[PromptPlayground-API] compare 请求: models={}, count={}",
        request.models(), request.models().size());
    List<PromptInvokeResult> results =
        playgroundService.compare(
            request.prompt(),
            request.userMessage(),
            request.models(),
            request.temperature(),
            request.maxTokens());
    return YdszResponse.success(results);
  }

  /**
   * Prompt 调用请求体（单次 invoke 和 stream 共用）。
   *
   * <p>prompt 为 Prompt 原文（作为 system message），userMessage 为触发消息（作为 user message）。
   * 推理参数均未可选，未传时使用系统默认值。
   *
   * @param prompt Prompt 原文（作为 system message，必填）
   * @param userMessage 用户消息（作为 user message；为空时发送默认占位）
   * @param model 模型名称（为空时使用 {@code ydzz.agent.llm.default-model} 配置值）
   * @param temperature 采样温度（[0.0, 2.0]；为空时使用默认值 0.7）
   * @param maxTokens 最大生成 Token 数；为空时使用默认值 2048）
   */
  public record PromptInvokeRequest(
      @NotBlank(message = "prompt 不能为空") String prompt,
      String userMessage,
      String model,
      Double temperature,
      Integer maxTokens) {}

  /**
   * 多模型对比请求体。
   *
   * <p>与 {@link PromptInvokeRequest} 类似，但 {@code models} 为列表（至少 1 个），
   * 系统将使用相同 Prompt 分别调用每个模型并收集指标。
   *
   * @param prompt Prompt 原文（作为 system message，所有模型共用，必填）
   * @param userMessage 用户消息（作为 user message；为空时发送默认占位）
   * @param models 待对比的模型名称列表（至少 1 个，必填）
   * @param temperature 采样温度（为空时使用默认值 0.7）
   * @param maxTokens 最大生成 Token 数；为空时使用默认值 2048）
   */
  public record PromptCompareRequest(
      @NotBlank(message = "prompt 不能为空") String prompt,
      String userMessage,
      @NotBlank(message = "models 不能为空") List<String> models,
      Double temperature,
      Integer maxTokens) {}
}
