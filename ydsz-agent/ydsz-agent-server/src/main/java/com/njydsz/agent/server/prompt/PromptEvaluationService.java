package com.njydsz.agent.server.prompt;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.server.config.AgentProperties;

/**
 * Prompt 评估服务
 *
 * <p>提供 Prompt 模板的"试运行"与评估能力：将模板渲染后发送到 LLM，采集响应质量指标（延迟、Token 用量、成本估算、响应长度），用于 Prompt 版本横向对比与上线前验证。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>单次评估：输入模板 + 变量 + 模型 → 采集完整指标
 *   <li>对比评估：两个 Prompt 使用相同输入执行 → 返回对比结果
 * </ul>
 *
 * <p>评估过程不影响线上配置，不写入 Prompt 版本历史。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class PromptEvaluationService {

  /** 默认用户消息模板（评估时用于触发 LLM 响应） */
  private static final String DEFAULT_EVAL_USER_MESSAGE = "请根据以上系统提示进行回复。";

  /** 估算成本：千 Prompt Token 单价（USD，GPT-4 级别定价近似） */
  private static final BigDecimal COST_PER_1K_PROMPT_TOKENS = new BigDecimal("0.001");

  /** 估算成本：千补全 Token 单价（USD，GPT-4 级别定价近似） */
  private static final BigDecimal COST_PER_1K_COMPLETION_TOKENS = new BigDecimal("0.002");

  private final PromptManagementService promptManagementService;
  private final LlmClient llmClient;
  private final AgentProperties properties;

  public PromptEvaluationService(
      PromptManagementService promptManagementService,
      LlmClient llmClient,
      AgentProperties properties) {
    this.promptManagementService = promptManagementService;
    this.llmClient = llmClient;
    this.properties = properties;
  }

  /**
   * 评估 Prompt 模板（使用指定变量渲染后发送到 LLM）。
   *
   * @param templateCode Prompt 模板编码
   * @param variables 渲染变量映射
   * @param userMessage 评估用用户消息（null 时使用默认占位消息）
   * @param model 指定模型（null 时使用默认模型）
   * @return 评估结果（含指标）
   */
  public PromptEvaluationResult evaluate(
      String templateCode, Map<String, Object> variables, String userMessage, String model) {
    String renderedPrompt = promptManagementService.render(templateCode, variables);
    String evalUserMessage = userMessage != null ? userMessage : DEFAULT_EVAL_USER_MESSAGE;
    String evalModel = model != null ? model : properties.getLlm().getDefaultModel();

    log.info("[PromptEval] 开始评估: template={}, model={}", templateCode, evalModel);

    // 构造请求：System = 渲染后的模板，User = 评估消息
    ChatRequest request =
        ChatRequest.builder()
            .model(evalModel)
            .messages(
                List.of(
                    ChatMessage.system(renderedPrompt),
                    ChatMessage.user(evalUserMessage, null)))
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .build();

    long startTime = System.currentTimeMillis();
    ChatResponse response = llmClient.chat(request);
    long durationMs = System.currentTimeMillis() - startTime;

    TokenUsage usage = response.getUsage();
    int promptTokens = usage != null ? usage.getPromptTokens() : 0;
    int completionTokens = usage != null ? usage.getCompletionTokens() : 0;
    int totalTokens = usage != null ? usage.getTotalTokens() : 0;
    String content = response.getContent() != null ? response.getContent() : "";

    // 估算成本（基于 GPT-4 级别定价的微美元近似）
    BigDecimal estimatedCostUsd = BigDecimal.valueOf(promptTokens)
        .multiply(COST_PER_1K_PROMPT_TOKENS)
        .add(BigDecimal.valueOf(completionTokens).multiply(COST_PER_1K_COMPLETION_TOKENS))
        .divide(BigDecimal.valueOf(1000), 6, java.math.RoundingMode.HALF_UP);

    log.info(
        "[PromptEval] 评估完成: template={}, duration={}ms, tokens={}, cost={}",
        templateCode, durationMs, totalTokens, estimatedCostUsd.toPlainString());

    return new PromptEvaluationResult(
        templateCode,
        renderedPrompt,
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
   * 对比评估两个 Prompt 模板（相同输入、相同模型）。
   *
   * @param templateCodeA 模板 A 编码
   * @param templateCodeB 模板 B 编码
   * @param variables 渲染变量映射
   * @param userMessage 评估用用户消息
   * @param model 指定模型（null 时使用默认模型）
   * @return 对比结果（A 与 B 的评估指标并排对比）
   */
  public PromptComparisonResult compare(
      String templateCodeA,
      String templateCodeB,
      Map<String, Object> variables,
      String userMessage,
      String model) {
    log.info("[PromptEval] 开始对比: A={}, B={}", templateCodeA, templateCodeB);
    PromptEvaluationResult resultA = evaluate(templateCodeA, variables, userMessage, model);
    PromptEvaluationResult resultB = evaluate(templateCodeB, variables, userMessage, model);
    return new PromptComparisonResult(resultA, resultB);
  }

  /**
   * Prompt 单次评估结果。
   *
   * @param templateCode 模板编码
   * @param renderedPrompt 渲染后的完整 Prompt（供审查）
   * @param model 使用的模型
   * @param durationMs 端到端耗时（毫秒）
   * @param promptTokens 输入 Token 数
   * @param completionTokens 输出 Token 数
   * @param totalTokens 总 Token 数
   * @param estimatedCostUsd 估算成本（USD）
   * @param responseLength 响应字符长度
   * @param responseContent 响应内容原文
   * @param evaluatedAt 评估时间
   */
  public record PromptEvaluationResult(
      String templateCode,
      String renderedPrompt,
      String model,
      long durationMs,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      BigDecimal estimatedCostUsd,
      int responseLength,
      String responseContent,
      LocalDateTime evaluatedAt) {}

  /**
   * Prompt 模板对比评估结果。
   *
   * @param resultA 模板 A 评估结果
   * @param resultB 模板 B 评估结果
   */
  public record PromptComparisonResult(
      PromptEvaluationResult resultA,
      PromptEvaluationResult resultB) {

    /**
     * 计算 Token 节省率（正值表示 B 比 A 省 Token）。
     *
     * @return 节省率（0.1 = 10%）
     */
    public double tokenSavingRate() {
      if (resultA.totalTokens() == 0) {
        return 0;
      }
      return (double) (resultA.totalTokens() - resultB.totalTokens()) / resultA.totalTokens();
    }

    /**
     * 计算延迟差异（正值表示 B 比 A 更快）。
     *
     * @return 毫秒差（A - B）
     */
    public long latencyDiffMs() {
      return resultA.durationMs() - resultB.durationMs();
    }

    /**
     * 计算成本节省率（正值表示 B 比 A 更便宜）。
     *
     * @return 节省率（0.1 = 10%）
     */
    public double costSavingRate() {
      if (resultA.estimatedCostUsd().compareTo(BigDecimal.ZERO) == 0) {
        return 0;
      }
      return resultA.estimatedCostUsd().subtract(resultB.estimatedCostUsd())
          .divide(resultA.estimatedCostUsd(), 4, java.math.RoundingMode.HALF_UP).doubleValue();
    }
  }
}
