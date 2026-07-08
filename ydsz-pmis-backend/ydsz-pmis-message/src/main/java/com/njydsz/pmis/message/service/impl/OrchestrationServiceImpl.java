package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.dto.OrchestrationFlowDTO;
import com.njydsz.pmis.message.dto.OrchestrationNodeDTO;
import com.njydsz.pmis.message.dto.OrchestrationResultVO;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 消息编排引擎实现。
 *
 * <p>P1-9: 基于 DAG 拓扑排序执行消息编排流程：
 * <ol>
 *   <li>验证 DAG 合法性（无环检测）</li>
 *   <li>按拓扑序逐个执行节点</li>
 *   <li>节点依赖全部成功后才执行</li>
 *   <li>支持 SpEL 条件表达式</li>
 *   <li>节点失败按策略处理：CONTINUE / ABORT / RETRY（最多 3 次）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationServiceImpl implements OrchestrationService {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final int MAX_RETRY = 3;

    private final MessageService messageService;

    @Override
    public OrchestrationResultVO execute(OrchestrationFlowDTO flow) {
        if (flow == null || CollectionUtils.isEmpty(flow.getNodes())) {
            return new OrchestrationResultVO(null, "FAILED", 0, 0, 0, 0, Map.of(), "流程或节点为空");
        }
        String flowId = StringUtils.hasText(flow.getFlowId())
                ? flow.getFlowId() : SnowflakeIdGenerator.nextIdStr();
        log.info("[Orchestration] 流程开始: flowId={} nodes={}", flowId, flow.getNodes().size());

        // DAG 校验
        List<String> topoOrder;
        try {
            topoOrder = topologicalSort(flow.getNodes());
        } catch (IllegalStateException e) {
            return new OrchestrationResultVO(flowId, "FAILED", 0, 0, 0,
                    flow.getNodes().size(), Map.of(), e.getMessage());
        }

        // 节点映射
        Map<String, OrchestrationNodeDTO> nodeMap = flow.getNodes().stream()
                .collect(Collectors.toMap(OrchestrationNodeDTO::getNodeId, n -> n));
        // 执行结果
        Map<String, String> nodeResults = new ConcurrentHashMap<>();
        Map<String, Boolean> nodeSuccess = new ConcurrentHashMap<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (String nodeId : topoOrder) {
            OrchestrationNodeDTO node = nodeMap.get(nodeId);
            // 检查依赖是否全部成功
            if (!CollectionUtils.isEmpty(node.getDependsOn())) {
                boolean allDepsSuccess = node.getDependsOn().stream()
                        .allMatch(dep -> Boolean.TRUE.equals(nodeSuccess.get(dep)));
                if (!allDepsSuccess) {
                    nodeResults.put(nodeId, "SKIPPED (依赖未成功)");
                    skippedCount++;
                    continue;
                }
            }
            // 检查条件表达式
            if (StringUtils.hasText(node.getCondition())) {
                try {
                    StandardEvaluationContext ctx = new StandardEvaluationContext();
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
                    log.warn("[Orchestration] 条件表达式求值失败: nodeId={} err={}", nodeId, e.getMessage());
                }
            }
            // 执行节点
            boolean nodeOk = executeNode(node, flow, nodeResults);
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

        String status = failedCount > 0 && successCount == 0 ? "FAILED" : "COMPLETED";
        log.info("[Orchestration] 流程完成: flowId={} status={} success={} failed={} skipped={}",
                flowId, status, successCount, failedCount, skippedCount);
        return new OrchestrationResultVO(flowId, status, successCount, failedCount, skippedCount,
                flow.getNodes().size(), nodeResults, null);
    }

    /**
     * 执行单个编排节点（支持重试）。
     */
    private boolean executeNode(OrchestrationNodeDTO node, OrchestrationFlowDTO flow,
                                Map<String, String> nodeResults) {
        int retryCount = "RETRY".equalsIgnoreCase(node.getOnFailure()) ? MAX_RETRY : 1;
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                MessageRequest request = new MessageRequest();
                request.setChannel(node.getChannel());
                request.setTemplateCode(node.getTemplateCode());
                request.setReceiver(node.getReceiver());
                request.setParams(node.getParams());
                request.setBizType(flow.getBizType());
                request.setBizId(flow.getBizId());
                request.setMessageId(SnowflakeIdGenerator.nextIdStr());
                MessageResult result = messageService.send(request);
                if (result != null && result.isSuccess()) {
                    nodeResults.put(node.getNodeId(), "SUCCESS: " + result.getProviderTraceId());
                    return true;
                }
                String errMsg = result != null ? result.getErrorMessage() : "未知错误";
                if (attempt < retryCount) {
                    log.info("[Orchestration] 节点重试: nodeId={} attempt={}/{}", node.getNodeId(), attempt, retryCount);
                    Thread.sleep(1000L * attempt);
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
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
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
