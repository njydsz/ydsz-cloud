package com.njydsz.workflow.server.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.workflow.domain.gateway.AgentServiceClient.AgentExecutionResult;
import com.njydsz.workflow.domain.gateway.AgentServiceClient;
import com.njydsz.workflow.domain.vo.AiAgentNodeConfigVO.FallbackStrategy;
import com.njydsz.workflow.domain.vo.AiAgentNodeConfigVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.server.engine.impl.FlowVariableReplacer;

/**
 * P0-5: AI 审批节点执行器
 *
 * <p>负责执行 {@link com.njydsz.workflow.domain.enums.FlowNodeType#AI_AGENT} 类型节点的智能审批逻辑，
 * 不创建人工任务。执行流程：
 *
 * <ol>
 *   <li>解析节点 ext JSON 中的 AI Agent 配置（agentId / promptTemplate / outputSchema / fallbackStrategy /
 *       retryMax / timeoutMs）
 *   <li>使用 {@link FlowVariableReplacer} 替换提示词模板中的 {@code ${variable}} 占位符
 *   <li>调用 {@link AgentServiceClient#execute} 同步执行 Agent（支持超时控制）
 *   <li>解析输出结果，根据 approve/reject 决策自动推进流程
 *   <li>异常时根据 fallbackStrategy 执行兜底逻辑（AUTO_PASS / AUTO_REJECT / TRANSFER_ADMIN / RETRY）
 * </ol>
 *
 * <p>ext JSON 配置示例：
 *
 * <pre>
 * {
 *   "agentId": "agent-001",
 *   "promptTemplate": "请根据以下信息判断是否通过审批：申请人=${applicant}，金额=${amount}...",
 *   "outputSchema": "{\"type\":\"object\",\"properties\":{\"approve\":{\"type\":\"boolean\"},\"reason\":{\"type\":\"string\"}}}",
 *   "fallbackStrategy": "AUTO_PASS",
 *   "retryMax": 1,
 *   "timeoutMs": 30000
 * }
 * </pre>
 *
 * <p>借鉴 Flowlong 的「AI 审批」概念，将 AI Agent 作为流程节点执行器，
 * 实现自然语言驱动的审批决策自动化。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowAiAgentNodeExecutor {

  /** AI Agent 执行使用的线程池名称 */
  private static final String THREAD_POOL_NAME = "flow-ai-agent-executor";

  /** 重试基础延迟（毫秒，指数退避起点） */
  private static final long RETRY_BASE_DELAY_MS = 1000L;

  /** 重试最大延迟（毫秒，指数退避上限） */
  private static final long RETRY_MAX_DELAY_MS = 5000L;

  /** 兜底线程池核心线程数 */
  private static final int FALLBACK_POOL_CORE_SIZE = 2;

  /** 兜底线程池最大线程数 */
  private static final int FALLBACK_POOL_MAX_SIZE = 4;

  /** 兜底线程池队列容量 */
  private static final int FALLBACK_POOL_QUEUE_CAPACITY = 256;

  /** 提示词模板变量替换器 */
  private final FlowVariableReplacer variableReplacer;

  /** AI Agent 服务客户端 */
  private final AgentServiceClient agentServiceClient;

  /** common-thread 声明式线程池（P0-5：ydsz.thread.pools.flow-ai-agent-executor，可选依赖安全降级） */
  private final ObjectProvider<ThreadPoolTaskExecutor> executorProvider;

  /**
   * 构造器注入依赖。
   *
   * @param variableReplacer 变量替换器
   * @param agentServiceClient AI Agent 服务客户端
   * @param executorProvider 声明式线程池提供者（按名匹配 ydsz-flow-ai-agent-executor）
   */
  public FlowAiAgentNodeExecutor(FlowVariableReplacer variableReplacer,
      AgentServiceClient agentServiceClient,
      ObjectProvider<ThreadPoolTaskExecutor> executorProvider) {
    this.variableReplacer = variableReplacer;
    this.agentServiceClient = agentServiceClient;
    this.executorProvider = executorProvider;
    log.info("[Flow-AI-Agent] AI 审批节点执行器已初始化");
  }

  /**
   * 获取 Agent 执行线程池。
   *
   * <p>优先使用 common-thread 声明式线程池（云顶规范 16.4，按线程名前缀匹配），
   * 未配置时经 {@link ExecutorUtils} 工厂创建模块级兜底池（common-thread 授权场景）。
   *
   * @return AI Agent 执行线程池
   */
  private ExecutorService resolveExecutor() {
    for (ThreadPoolTaskExecutor candidate : executorProvider) {
      String prefix = candidate.getThreadNamePrefix();
      if (prefix != null && prefix.contains(THREAD_POOL_NAME)) {
        return candidate.getThreadPoolExecutor();
      }
    }
    return ExecutorUtils.builder()
        .corePoolSize(FALLBACK_POOL_CORE_SIZE)
        .maxPoolSize(FALLBACK_POOL_MAX_SIZE)
        .queueCapacity(FALLBACK_POOL_QUEUE_CAPACITY)
        .threadNamePrefix(THREAD_POOL_NAME + "-")
        .build();
  }

  /**
   * 执行 AI 审批节点。
   *
   * <p>组装请求 → 调用 Agent → 超时控制 → 解析输出 → 返回审批结论。
   *
   * @param node 节点视图对象
   * @param instanceId 流程实例 ID
   * @param variables 流程实例变量
   * @return true-审批通过（继续推进）；false-审批驳回
   * @throws SysException Agent 配置非法、执行异常且无法兜底时抛出
   */
  public boolean execute(FlowNodeVO node, String instanceId, Map<String, Object> variables) {
    AiAgentNodeConfigVO config = node.getAiAgentNodeConfig();
    String nodeCode = node.getNodeCode();

    // 校验 agentId
    if (!config.hasValidAgentId()) {
      log.warn("[Flow-AI-Agent] 节点 {} 未配置 agentId，触发兜底策略: {}", nodeCode,
          config.getFallbackStrategy());
      return applyFallback(config, instanceId, nodeCode, "未配置 agentId");
    }

    // 替换提示词模板变量
    String resolvedPrompt = resolvePrompt(config.getPromptTemplate(), variables);
    log.info("[Flow-AI-Agent] 实例 {} 节点 {} 开始执行 AI 审批, agentId={}, timeout={}ms", instanceId,
        nodeCode, config.getAgentId(), config.getTimeoutMs());

    // 构建上下文
    Map<String, Object> context = buildContext(instanceId, nodeCode, variables);

    // 执行（带重试）
    AgentExecutionResult result = executeWithRetry(config, resolvedPrompt, context, instanceId,
        nodeCode);

    if (result == null) {
      log.warn("[Flow-AI-Agent] 实例 {} 节点 {} Agent 返回 null，触发兜底策略", instanceId, nodeCode);
      return applyFallback(config, instanceId, nodeCode, "Agent 返回 null");
    }

    log.info("[Flow-AI-Agent] 实例 {} 节点 {} AI 审批完成, approve={}, confidence={}, reason={}",
        instanceId, nodeCode, result.approve(), result.confidence(), result.reason());

    return result.approve();
  }

  /**
   * 带重试的 Agent 执行。
   *
   * @param config AI Agent 配置
   * @param prompt 解析后的提示词
   * @param context 上下文变量
   * @param instanceId 实例 ID（日志用）
   * @param nodeCode 节点编码（日志用）
   * @return Agent 执行结果，全部重试失败时返回 null
   */
  private AgentExecutionResult executeWithRetry(AiAgentNodeConfigVO config, String prompt,
      Map<String, Object> context, String instanceId, String nodeCode) {
    int maxAttempts = config.isRetryFallback() ? config.getRetryMax() + 1 : 1;
    AgentExecutionResult lastResult = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        lastResult = executeSingleAttempt(config, prompt, context);
        // 非错误结果（通过或驳回）直接返回
        if (lastResult != null && lastResult.confidence() > 0) {
          return lastResult;
        }
      } catch (SysException e) {
        throw e;
      } catch (Exception e) {
        log.warn("[Flow-AI-Agent] 实例 {} 节点 {} 第 {}/{} 次执行异常: {}", instanceId, nodeCode, attempt,
            maxAttempts, e.getMessage());
        if (attempt >= maxAttempts) {
          return null;
        }
        // 重试前等待（指数退避）
        sleepBeforeRetry(attempt);
      }
    }
    return lastResult;
  }

  /**
   * 单次 Agent 执行（带超时控制）。
   *
   * @param config AI Agent 配置
   * @param prompt 提示词
   * @param context 上下文
   * @return Agent 执行结果
   */
  private AgentExecutionResult executeSingleAttempt(AiAgentNodeConfigVO config, String prompt,
      Map<String, Object> context) {
    // 使用 CompletableFuture + 线程池实现超时控制
    CompletableFuture<AgentExecutionResult> future = CompletableFuture.supplyAsync(
        () -> agentServiceClient.execute(config.getAgentId(), prompt, context, config.getTimeoutMs()),
        resolveExecutor());

    try {
      return future.get(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw SysException.builder()
          .resultCode(YdszResultCode.REQUEST_TIMEOUT)
          .key("error.workflow.ai.agent.timeout")
          .params(config.getAgentId(), config.getTimeoutMs())
          .cause(e)
          .build();
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BIZ_ERROR)
          .key("error.workflow.ai.agent.execution.error")
          .params(config.getAgentId(), e.getMessage())
          .cause(e)
          .build();
    }
  }

  /**
   * 应用兜底策略。
   *
   * @param config AI Agent 配置
   * @param instanceId 实例 ID（日志用）
   * @param nodeCode 节点编码（日志用）
   * @param cause 触发兜底的原因
   * @return 兜底后的审批决策
   */
  private boolean applyFallback(AiAgentNodeConfigVO config, String instanceId, String nodeCode,
      String cause) {
    FallbackStrategy strategy = config.getFallbackStrategy();
    log.warn("[Flow-AI-Agent] 实例 {} 节点 {} 触发兜底策略: {}, 原因: {}", instanceId, nodeCode, strategy,
        cause);

    switch (strategy) {
      case AUTO_PASS:
        log.info("[Flow-AI-Agent] 实例 {} 节点 {} 兜底策略: 自动通过", instanceId, nodeCode);
        return true;
      case AUTO_REJECT:
        log.info("[Flow-AI-Agent] 实例 {} 节点 {} 兜底策略: 自动驳回", instanceId, nodeCode);
        return false;
      case TRANSFER_ADMIN:
        // 转交管理员（由调用方处理）
        log.info("[Flow-AI-Agent] 实例 {} 节点 {} 兜底策略: 转交管理员", instanceId, nodeCode);
        throw SysException.builder()
            .resultCode(YdszResultCode.BIZ_ERROR)
            .key("error.workflow.ai.agent.transfer.admin")
            .params(instanceId, nodeCode, cause)
            .build();
      case RETRY:
        // 重试已穷尽，最终兜底为自动通过
        log.warn("[Flow-AI-Agent] 实例 {} 节点 {} 重试已穷尽，最终兜底为自动通过", instanceId, nodeCode);
        return true;
      default:
        log.warn("[Flow-AI-Agent] 实例 {} 节点 {} 未知兜底策略: {}, 默认自动通过", instanceId, nodeCode,
            strategy);
        return true;
    }
  }

  /**
   * 替换提示词模板中的变量占位符。
   *
   * @param template 提示词模板
   * @param variables 流程变量
   * @return 替换后的提示词
   */
  private String resolvePrompt(String template, Map<String, Object> variables) {
    if (template == null || template.isBlank()) {
      log.warn("[Flow-AI-Agent] 提示词模板为空");
      return "";
    }
    try {
      return variableReplacer.replacePlaceholders(template, variables);
    } catch (Exception e) {
      log.warn("[Flow-AI-Agent] 提示词变量替换失败，使用原始模板: {}", e.getMessage());
      return template;
    }
  }

  /**
   * 构建 Agent 执行上下文。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @param variables 流程变量
   * @return 上下文 Map
   */
  private Map<String, Object> buildContext(String instanceId, String nodeCode,
      Map<String, Object> variables) {
    Map<String, Object> context = new HashMap<>(16);
    if (variables != null) {
      context.putAll(variables);
    }
    context.put("_instanceId", instanceId);
    context.put("_nodeCode", nodeCode);
    return context;
  }

  /**
   * 重试前等待（指数退避策略）。
   *
   * @param attempt 当前尝试次数（从 1 开始）
   */
  private void sleepBeforeRetry(int attempt) {
    try {
      long delayMs = Math.min(RETRY_BASE_DELAY_MS * (1L << (attempt - 1)), RETRY_MAX_DELAY_MS);
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
