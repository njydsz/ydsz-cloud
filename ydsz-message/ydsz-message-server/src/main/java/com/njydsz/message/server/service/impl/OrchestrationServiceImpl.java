package com.njydsz.message.server.service.impl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.core.OrchestrationFlowDTO;
import com.njydsz.message.domain.dto.core.OrchestrationNodeDTO;
import com.njydsz.message.domain.dto.core.OrchestrationResultVO;
import com.njydsz.message.server.service.core.MessageService;
import com.njydsz.message.server.service.core.OrchestrationService;

/**
 * 消息编排服务实现。
 *
 * <p>复杂消息场景的编排引擎：多渠道组合发送（A 渠道失败回退 B）、分支判断（用户标签 → 渠道）、
 *
 * <p>定时/重试/补偿策略。对应实体 {@code ydsz_msg_orchestration}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationServiceImpl implements OrchestrationService {

  /** SpEL 表达式解析器（条件求值） */
  private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

  /** 节点最大重试次数 */
  private static final int MAX_RETRY = 3;

  /** 消息发送服务（节点执行时调用） */
  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final MessageService messageService;

  @Override
  public OrchestrationResultVO execute(OrchestrationFlowDTO flow) {
    if (flow == null || CollectionUtils.isEmpty(flow.getNodes())) {
      return new OrchestrationResultVO(null, "FAILED", 0, 0, 0, 0, Map.of(), "流程或节点为空");
    }
    String flowId =
        StringUtils.hasText(flow.getFlowId())
            ? flow.getFlowId()
            : String.valueOf(snowflakeIdGenerator.nextId());
    log.info("[Orchestration] 流程开始: flowId={} nodes={}", flowId, flow.getNodes().size());

    // DAG 校验
    List<String> topoOrder;
    try {
      topoOrder = topologicalSort(flow.getNodes());
    } catch (IllegalStateException e) {
      return new OrchestrationResultVO(
          flowId, "FAILED", 0, 0, 0, flow.getNodes().size(), Map.of(), e.getMessage());
    }

    // 节点映射
    Map<String, OrchestrationNodeDTO> nodeMap =
        flow.getNodes().stream().collect(Collectors.toMap(OrchestrationNodeDTO::getNodeId, n -> n));
    // 执行结果
    Map<String, String> nodeResults = new ConcurrentHashMap<>();
    Map<String, Boolean> nodeSuccess = new ConcurrentHashMap<>();

    // P1-2: 流程级超时控制
    long flowStartNanos = System.nanoTime();
    int flowTimeoutSeconds =
        flow.getGlobalTimeoutSeconds() != null ? flow.getGlobalTimeoutSeconds() : 300;
    long flowTimeoutNanos = TimeUnit.SECONDS.toNanos(flowTimeoutSeconds);

    int successCount = 0;
    int failedCount = 0;
    int skippedCount = 0;
    boolean timeoutTriggered = false;

    for (String nodeId : topoOrder) {
      // P1-2: 检查流程是否已超时
      long elapsedNanos = System.nanoTime() - flowStartNanos;
      if (elapsedNanos > flowTimeoutNanos) {
        log.warn(
            "[Orchestration] 流程超时,剩余节点跳过: flowId={} elapsed={}s timeout={}s",
            flowId,
            TimeUnit.NANOSECONDS.toSeconds(elapsedNanos),
            flowTimeoutSeconds);
        timeoutTriggered = true;
        break;
      }

      OrchestrationNodeDTO node = nodeMap.get(nodeId);
      // 检查依赖是否全部成功
      if (!CollectionUtils.isEmpty(node.getDependsOn())) {
        boolean allDepsSuccess =
            node.getDependsOn().stream().allMatch(dep -> Boolean.TRUE.equals(nodeSuccess.get(dep)));
        if (!allDepsSuccess) {
          nodeResults.put(nodeId, "SKIPPED (依赖未成功)");
          skippedCount++;
          continue;
        }
      }
      // 检查条件表达式
      if (StringUtils.hasText(node.getCondition())) {
        try {
          // P0-5 修复：使用 SimpleEvaluationContext 防止 SpEL 注入
          // 禁止 T(...) 类型引用、new 构造、方法调用，仅允许只读数据绑定
          SimpleEvaluationContext ctx =
              SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(nodeSuccess).build();
          ctx.setVariable("nodeSuccess", nodeSuccess);
          ctx.setVariable("nodeResults", nodeResults);
          Expression expr = SPEL_PARSER.parseExpression(node.getCondition());
          Boolean shouldExecute = expr.getValue(ctx, Boolean.class);
          if (Boolean.FALSE.equals(shouldExecute)) {
            nodeResults.put(nodeId, "SKIPPED (条件不满足)");
            skippedCount++;
            continue;
          }
        } catch (Exception e) {
          log.warn("[Orchestration] 条件表达式求值失败: nodeId={} err={}", nodeId, e.getMessage(), e);
        }
      }
      // 执行节点
      boolean nodeOk = executeNode(node, flow, nodeResults, flowStartNanos, flowTimeoutNanos);
      nodeSuccess.put(nodeId, nodeOk);
      if (nodeOk) {
        successCount++;
      } else {
        failedCount++;
        if ("ABORT".equalsIgnoreCase(node.getOnFailure())) {
          nodeResults.put(nodeId, nodeResults.getOrDefault(nodeId, "") + " [流程终止]");
          log.info("[Orchestration] 节点失败导致流程终止: flowId={} nodeId={}", flowId, nodeId);
          break;
        }
      }
    }

    // P1-2: 超时节点计入 skipped
    if (timeoutTriggered) {
      skippedCount = flow.getNodes().size() - successCount - failedCount - skippedCount;
      skippedCount = Math.max(skippedCount, 0);
    }

    String status;
    if (timeoutTriggered) {
      status = "TIMEOUT";
    } else if (failedCount > 0 && successCount == 0) {
      status = "FAILED";
    } else {
      status = "COMPLETED";
    }
    log.info(
        "[Orchestration] 流程完成: flowId={} status={} success={} failed={} skipped={} timeout={}",
        flowId,
        status,
        successCount,
        failedCount,
        skippedCount,
        timeoutTriggered);
    return new OrchestrationResultVO(
        flowId,
        status,
        successCount,
        failedCount,
        skippedCount,
        flow.getNodes().size(),
        nodeResults,
        timeoutTriggered ? "流程超时" : null);
  }

  /** 执行单个编排节点（支持重试）。 */
  private boolean executeNode(
      OrchestrationNodeDTO node,
      OrchestrationFlowDTO flow,
      Map<String, String> nodeResults,
      long flowStartNanos,
      long flowTimeoutNanos) {
    int retryCount = "RETRY".equalsIgnoreCase(node.getOnFailure()) ? MAX_RETRY : 1;
    for (int attempt = 1; attempt <= retryCount; attempt++) {
      // P1-2: 检查流程是否已超时
      if (System.nanoTime() - flowStartNanos > flowTimeoutNanos) {
        nodeResults.put(node.getNodeId(), "SKIPPED (流程超时)");
        return false;
      }
      try {
        MessageRequest request = new MessageRequest();
        request.setChannel(node.getChannel());
        request.setTemplateCode(node.getTemplateCode());
        request.setReceiver(node.getReceiver());
        request.setParams(node.getParams());
        request.setBizType(flow.getBizType());
        request.setBizId(flow.getBizId());
        request.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
        MessageResult result = messageService.send(request);
        if (result != null && result.isSuccess()) {
          nodeResults.put(node.getNodeId(), "SUCCESS: " + result.getProviderTraceId());
          return true;
        }
        String errMsg = result != null ? result.getErrorMessage() : "未知错误";
        if (attempt < retryCount) {
          log.info(
              "[Orchestration] 节点重试: nodeId={} attempt={}/{}",
              node.getNodeId(),
              attempt,
              retryCount);
          // P1-2: 使用可中断的 sleep 替代 Thread.sleep，以便响应超时
          long sleepMs = 1000L * attempt;
          long remainingNanos = flowTimeoutNanos - (System.nanoTime() - flowStartNanos);
          long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
          if (remainingMs <= 0) {
            nodeResults.put(node.getNodeId(), "FAILED: 流程超时");
            return false;
          }
          Thread.sleep(Math.min(sleepMs, remainingMs));
        } else {
          nodeResults.put(node.getNodeId(), "FAILED: " + errMsg);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        nodeResults.put(node.getNodeId(), "FAILED: 中断");
        return false;
      } catch (Exception e) {
        nodeResults.put(node.getNodeId(), "FAILED: " + e.getMessage());
        if (attempt >= retryCount) {
          return false;
        }
      }
    }
    return false;
  }

  /**
   * 拓扑排序（Kahn 算法），检测环。
   *
   * @param nodes 节点列表
   * @return 拓扑序节点 ID 列表
   * @throws IllegalStateException 检测到环时抛出
   */
  private List<String> topologicalSort(List<OrchestrationNodeDTO> nodes) {
    // P2-2: 使用 LinkedHashMap 保证拓扑排序的确定性
    Map<String, Integer> inDegree = new LinkedHashMap<>();
    Map<String, List<String>> adjacency = new LinkedHashMap<>();
    for (OrchestrationNodeDTO node : nodes) {
      inDegree.putIfAbsent(node.getNodeId(), 0);
      adjacency.putIfAbsent(node.getNodeId(), new ArrayList<>());
      if (!CollectionUtils.isEmpty(node.getDependsOn())) {
        for (String dep : node.getDependsOn()) {
          adjacency.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getNodeId());
          inDegree.merge(node.getNodeId(), 1, (a, b) -> a + b);
        }
      }
    }
    Queue<String> queue = new LinkedList<>();
    for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
      if (e.getValue() == 0) {
        queue.add(e.getKey());
      }
    }
    List<String> result = new ArrayList<>();
    while (!queue.isEmpty()) {
      String current = queue.poll();
      result.add(current);
      for (String next : adjacency.getOrDefault(current, List.of())) {
        int newDegree = inDegree.merge(next, -1, (a, b) -> a + b);
        if (newDegree == 0) {
          queue.add(next);
        }
      }
    }
    if (result.size() != inDegree.size()) {
      throw new IllegalStateException("DAG 检测到环，无法拓扑排序");
    }
    return result;
  }
}
