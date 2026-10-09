package com.njydsz.agent.server.prompt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * Prompt Playground 交互评估服务
 *
 * <p>提供 Prompt 模板的 LLM 直接调用能力，供前端 Prompt Debug 控制台使用：
 *
 * <ul>
 *   <li>{@link #invoke} — 单次 LLM 调用，返回完整响应与性能指标</li>
 *   <li>{@link #invokeStream} — 流式 LLM 调用，通过 chunkConsumer 推送增量内容</li>
 *   <li>{@link #compare} — 多模型对比，使用相同 Prompt 分别调用不同模型，返回并排指标</li>
 * </ul>
 *
 * <p>与 {@link PromptEvaluationService} 的区别：本类接受原始 prompt text（不经模板渲染），
 * 且支持自定义 temperature / maxTokens 等参数，面向 Prompt 开发者实时调试场景。
 *
 * <p>IO 密集型（LLM HTTP 调用），由 Controller 层在 {@code SsePushChannel} 包裹的虚拟线程中执行。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
public class PromptPlaygroundService {

  /** 估算成本：千 Prompt Token 单价（USD，GPT-4 级别定价近似） */
  private static final BigDecimal COST_PER_1K_PROMPT_TOKENS = new BigDecimal("0.001");

  /** 估算成本：千补全 Token 单价（USD，GPT-4 级别定价近似） */
  private static final BigDecimal COST_PER_1K_COMPLETION_TOKENS = new BigDecimal("0.002");

  /** 成本估算的 Token 换算基数（每千 Token 计价） */
  private static final int TOKENS_PER_KILO = 1000;

  /** 成本估算小数精度 */
  private static final int COST_SCALE = 6;

  /** 缺省温度 */
  private static final double DEFAULT_TEMPERATURE = 0.7;

  /** 缺省最大 Token 数 */
  private static final int DEFAULT_MAX_TOKENS = 2048;

  private final PromptEvaluationService evaluationService;
  private final LlmClient llmClient;
  private final AgentProperties properties;

  public PromptPlaygroundService(
      PromptEvaluationService evaluationService,
      LlmClient llmClient,
      AgentProperties properties) {
    this.evaluationService = evaluationService;
    this.llmClient = llmClient;
    this.properties = properties;
  }

  /**
   * 单次 LLM 调用（将 prompt 作为 system message 发送到 LLM）。
   *
   * <p>将 prompt 作为 system message、userMessage 作为 user message 发送到 LLM，
   * 收集延迟、Token 用量、成本等指标，返回 {@link PromptInvokeResult}。
   *
   * @param prompt Prompt 原文（作为 system message）
   * @param userMessage 用户消息（作为 user message；为空时发送默认占位）
   * @param model 模型名称（为空时使用默认模型）
   * @param temperature 采样温度（为空时使用默认值 0.7）
   * @param maxTokens 最大生成 Token 数（为空时使用默认值 2048）
   * @return Prompt 调用结果（含指标和响应内容）
   */
  public PromptInvokeResult invoke(
      String prompt,
      String userMessage,
      String model,
      Double temperature,
      Integer maxTokens) {
    if (prompt == null || prompt.isBlank()) {
      throw new BusinessException(AgentExceptionCode.PARAM_ERROR);
    }
    String evalUserMessage =
        (userMessage != null && !userMessage.isBlank()) ? userMessage : "请根据系统提示进行回复。";
    String evalModel =
        (model != null && !model.isBlank()) ? model : properties.getLlm().getDefaultModel();
    double evalTemperature = temperature != null ? temperature : DEFAULT_TEMPERATURE;
    int evalMaxTokens = maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;

    log.info("[PromptPlayground] invoke: model={}, temperature={}, maxTokens={}",
        evalModel, evalTemperature, evalMaxTokens);

    ChatRequest request =
        ChatRequest.builder()
            .model(evalModel)
            .messages(
                List.of(
                    ChatMessage.system(prompt),
                    ChatMessage.user(evalUserMessage, null)))
            .temperature(evalTemperature)
            .maxTokens(evalMaxTokens)
            .build();

    long startTime = System.currentTimeMillis();
    ChatResponse response;
    try {
      response = llmClient.chat(request);
    } catch (Exception e) {
      log.warn("[PromptPlayground] LLM 调用失败: model={}, error={}", evalModel, e.getMessage());
      throw BusinessException.of(AgentExceptionCode.LLM_CALL_FAILED).params(evalModel);
    }
    long durationMs = System.currentTimeMillis() - startTime;

    TokenUsage usage = response.getUsage();
    int promptTokens = usage != null ? usage.getPromptTokens() : 0;
    int completionTokens = usage != null ? usage.getCompletionTokens() : 0;
    int totalTokens = usage != null ? usage.getTotalTokens() : 0;
    String content = response.getContent() != null ? response.getContent() : "";

    BigDecimal estimatedCostUsd =
        BigDecimal.valueOf(promptTokens)
            .multiply(COST_PER_1K_PROMPT_TOKENS)
            .add(BigDecimal.valueOf(completionTokens).multiply(COST_PER_1K_COMPLETION_TOKENS))
            .divide(BigDecimal.valueOf(TOKENS_PER_KILO), COST_SCALE, RoundingMode.HALF_UP);

    log.info(
        "[PromptPlayground] invoke 完成: model={}, duration={}ms, tokens={}, cost={}",
        evalModel, durationMs, totalTokens, estimatedCostUsd.toPlainString());

    return new PromptInvokeResult(
        evalModel,
        durationMs,
        promptTokens,
        completionTokens,
        totalTokens,
        estimatedCostUsd,
        content.length(),
        content,
        LocalDateTime.now());
  }

  /**
   * 流式 LLM 调用（SSE 逐 token 推送）。
   *
   * <p>将 prompt 作为 system message 发送到 LLM，通过 {@code chunkConsumer} 推送每个 token 块。
   * 最终 chunk 的 {@link ChatChunk#isFinished()} 返回 true，携带完整用量数据。
   *
   * <p>本方法本身不处理 SSE 框架，仅消费 LLM 的 chunk；由 Controller 层
   * 在 {@code SsePushChannel} 虚拟线程中调用本方法，将 chunk 转为 SSE 帧。
   *
   * @param prompt Prompt 原文（作为 system message）
   * @param userMessage 用户消息（作为 user message；为空时使用默认占位）
   * @param model 模型名称（为空时使用默认模型）
   * @param temperature 采样温度（为空时使用默认值 0.7）
   * @param maxTokens 最大生成 Token 数（为空时使用默认值 2048）
   * @param chunkConsumer 流式块消费者
   */
  public void invokeStream(
      String prompt,
      String userMessage,
      String model,
      Double temperature,
      Integer maxTokens,
      Consumer<ChatChunk> chunkConsumer) {
    if (prompt == null || prompt.isBlank()) {
      throw new BusinessException(AgentExceptionCode.PARAM_ERROR);
    }
    if (chunkConsumer == null) {
      throw new BusinessException(AgentExceptionCode.PARAM_ERROR);
    }
    String evalUserMessage =
        (userMessage != null && !userMessage.isBlank()) ? userMessage : "请根据系统提示进行回复。";
    String evalModel =
        (model != null && !model.isBlank()) ? model : properties.getLlm().getDefaultModel();
    double evalTemperature = temperature != null ? temperature : DEFAULT_TEMPERATURE;
    int evalMaxTokens = maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;

    log.info("[PromptPlayground] invokeStream: model={}, temperature={}, maxTokens={}",
        evalModel, evalTemperature, evalMaxTokens);

    ChatRequest request =
        ChatRequest.builder()
            .model(evalModel)
            .messages(
                List.of(
                    ChatMessage.system(prompt),
                    ChatMessage.user(evalUserMessage, null)))
            .temperature(evalTemperature)
            .maxTokens(evalMaxTokens)
            .isStream(true)
            .build();

    long startTime = System.currentTimeMillis();
    try {
      llmClient.stream(request, chunkConsumer);
    } catch (Exception e) {
      long durationMs = System.currentTimeMillis() - startTime;
      log.warn("[PromptPlayground] 流式 LLM 调用失败: model={}, duration={}ms, error={}",
          evalModel, durationMs, e.getMessage());
      throw BusinessException.of(AgentExceptionCode.LLM_CALL_FAILED).params(evalModel);
    }

    long durationMs = System.currentTimeMillis() - startTime;
    log.info("[PromptPlayground] invokeStream 完成: model={}, duration={}ms", evalModel, durationMs);
  }

  /**
   * 多模型对比：使用相同 Prompt 分别调用不同 LLM 模型，返回并排指标。
   *
   * <p>对每个模型调用 {@link #invoke}，收集延迟、Token、成本指标。
   * 单个模型失败时记录错误继续处理其他模型，最终返回所有模型的结果列表。
   *
   * @param prompt Prompt 原文（作为 system message，所有模型共用）
   * @param userMessage 用户消息（作为 user message；为空时使用默认占位）
   * @param models 待对比的模型名称列表（至少 1 个）
   * @param temperature 采样温度（为空时使用默认值 0.7）
   * @param maxTokens 最大生成 Token 数（为空时使用默认值 2048）
   * @return 各模型的调用结果列表（保持与输入 models 列表相同顺序）
   */
  public List<PromptInvokeResult> compare(
      String prompt,
      String userMessage,
      List<String> models,
      Double temperature,
      Integer maxTokens) {
    if (prompt == null || prompt.isBlank()) {
      throw new BusinessException(AgentExceptionCode.PARAM_ERROR);
    }
    if (models == null || models.isEmpty()) {
      throw new BusinessException(AgentExceptionCode.PARAM_ERROR);
    }

    log.info("[PromptPlayground] compare: models={}, temperature={}, maxTokens={}",
        models, temperature, maxTokens);

    List<PromptInvokeResult> results = new ArrayList<>(models.size());
    for (String model : models) {
      try {
        results.add(invoke(prompt, userMessage, model, temperature, maxTokens));
      } catch (Exception e) {
        log.warn("[PromptPlayground] 模型对比失败: model={}, error={}", model, e.getMessage());
        int errorPromptTokens = prompt.split("\\s+").length;
        results.add(
            new PromptInvokeResult(
                model,
                0L,
                errorPromptTokens,
                0,
                errorPromptTokens,
                BigDecimal.ZERO,
                0,
                "模型调用失败: " + e.getMessage(),
                LocalDateTime.now()));
      }
    }

    return results;
  }

  /**
   * Prompt 调用结果（Prompt Playground 专用，含指标和响应内容）。
   *
   * @param model 使用的模型
   * @param durationMs 端到端耗时（毫秒）
   * @param promptTokens 输入 Token 数
   * @param completionTokens 输出 Token 数
   * @param totalTokens 总 Token 数
   * @param estimatedCostUsd 估算成本（USD）
   * @param responseLength 响应字符长度
   * @param responseContent 响应内容原文
   * @param invokedAt 调用时间
   */
  public record PromptInvokeResult(
      String model,
      long durationMs,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      BigDecimal estimatedCostUsd,
      int responseLength,
      String responseContent,
      LocalDateTime invokedAt) {}
}
