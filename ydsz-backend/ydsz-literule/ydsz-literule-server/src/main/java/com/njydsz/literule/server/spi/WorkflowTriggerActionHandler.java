package com.njydsz.literule.server.spi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.workflow.api.client.WorkflowServiceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作流触发动作处理器
 *
 * <p>规则触发后自动启动关联的工作流流程实例，实现规则与工作流深度联动。
 * 依赖 {@code ydsz-workflow-api} 模块提供的 {@link WorkflowServiceClient}。
 *
 * @since 2.1.0
 */
@Slf4j
public class WorkflowTriggerActionHandler implements RuleActionHandler {

    private final WorkflowServiceClient workflowClient;

    public WorkflowTriggerActionHandler(WorkflowServiceClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void handle(List<RuleResult> triggered, RuleContext context) {
        for (RuleResult result : triggered) {
            try {
                Map<String, Object> variables = new HashMap<>();
                if (context != null && context.getFacts() != null) {
                    variables.putAll(context.getFacts());
                }
                variables.put("ruleCode", result.getRuleCode());
                variables.put("ruleSeverity", result.getSeverity() != null ? result.getSeverity().name() : null);
                variables.put("ruleTitle", result.getTitle());
                workflowClient.startProcess(variables);
                log.debug("[LiteRule-Action] 工作流已触发: ruleCode={}", result.getRuleCode());
            } catch (Exception e) {
                log.warn("[LiteRule-Action] 工作流触发失败: ruleCode={}, error={}",
                        result.getRuleCode(), e.getMessage());
            }
        }
    }
}