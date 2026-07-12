package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.workflow.api.client.WorkflowServiceClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流触发动作处理器（P2-1 规则与工作流深度联动）
 *
 * <p>当规则触发时，自动启动关联的工作流流程实例。
 * 流程定义 Key 来源（按优先级）：
 * <ol>
 *   <li>{@code RuleResult} 的 {@code scope} 字段（格式: "workflow:{processKey}"）</li>
 *   <li>{@code RuleContext} facts 中的 {@code workflowProcessKey} 键</li>
 *   <li>{@code RuleContext} facts 中的 {@code workflow.processKey} 键（嵌套 Map）</li>
 * </ol>
 *
 * <h3>联动链路</h3>
 * <pre>
 * RuleEngine.evaluate
 *   → WorkflowTriggerActionHandler.onTriggered
 *     → WorkflowServiceClient Feign → workflow 模块
 *       → FlowInstanceController.start → 创建流程实例
 * </pre>
 *
 * <h3>业务关联</h3>
 * <p>启动流程时自动携带以下业务上下文变量：
 * <ul>
 *   <li>{@code businessType} — 来源规则场景（context.scenario）</li>
 *   <li>{@code businessId} — 来源业务 ID（context facts 中的 "businessId"）</li>
 *   <li>{@code ruleCode} — 触发规则的编码</li>
 *   <li>{@code ruleSeverity} — 规则严重度</li>
 *   <li>{@code ruleDescription} — 规则描述</li>
 *   <li>{@code triggeredAt} — 触发时间</li>
 * </ul>
 *
 * <h3>使用条件</h3>
 * <ul>
 *   <li>classpath 中存在 {@code WorkflowServiceClient}（由 ydsz-pmis-workflow-api 提供）</li>
 *   <li>规则结果中包含有效的工作流流程定义 Key</li>
 *   <li>未配置流程 Key 时静默跳过，不报错</li>
 * </ul>
 *
 * <p>使用 {@code ObjectProvider} 安全注入，当 workflow-api 不在 classpath 时不装配，
 * 不影响规则引擎核心功能。
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
public class WorkflowTriggerActionHandler implements RuleActionHandler {

    private final WorkflowServiceClient workflowServiceClient;

    public WorkflowTriggerActionHandler(WorkflowServiceClient workflowServiceClient) {
        this.workflowServiceClient = workflowServiceClient;
    }

    @Override
    public void onTriggered(List<RuleResult> results, RuleContext context) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (RuleResult result : results) {
            if (!BaseResponse.isTriggered()) {
                continue;
            }
            String processKey = resolveProcessKey(result, context);
            if (processKey == null || processKey.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> body = buildStartProcessBody(processKey, result, context);
                BaseResponse<String> startResult = workflowServiceClient.startProcess(body);
                if (startResult != null && startResult.isSuccess() && startResult.getData() != null) {
                    log.info("[LiteRule-Workflow] 工作流已启动: ruleCode={}, processKey={}, instanceId={}",
                            BaseResponse.getRuleCode(), processKey, startResult.getData());
                } else {
                    log.warn("[LiteRule-Workflow] 工作流启动失败: ruleCode={}, processKey={}, result={}",
                            BaseResponse.getRuleCode(), processKey,
                            startResult == null ? "null" : startResult.getCode());
                }
            } catch (Exception e) {
                log.warn("[LiteRule-Workflow] 工作流启动异常: ruleCode={}, processKey={}, error={}",
                        BaseResponse.getRuleCode(), processKey, e.getMessage());
            }
        }
    }

    @Override
    public String getHandlerId() {
        return "workflow-trigger";
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 构建启动流程实例的请求体
     *
     * <p>携带流程定义 Key 和业务上下文变量，供 workflow 模块创建实例并关联业务单据。
     *
     * @param processKey 流程定义 Key
     * @param result     规则结果
     * @param context    规则上下文
     * @return 请求体 Map
     */
    private Map<String, Object> buildStartProcessBody(String processKey, RuleResult result,
                                                       RuleContext context) {
        Map<String, Object> body = new HashMap<>();
        body.put("processKey", processKey);

        // 业务关联信息
        body.put("businessType", context.getScenario() != null ? context.getScenario() : "RULE_TRIGGER");
        Object businessId = context.get("businessId");
        if (businessId == null) {
            businessId = context.get("projectCode");
        }
        body.put("businessId", businessId != null ? businessId.toString() : BaseResponse.getRuleCode());

        // 规则上下文变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("ruleCode", BaseResponse.getRuleCode());
        variables.put("ruleName", BaseResponse.getRuleName());
        variables.put("ruleSeverity", BaseResponse.getSeverity() != null ? BaseResponse.getSeverity().getCode() : "INFO");
        variables.put("ruleDescription", BaseResponse.getDescription() != null ? BaseResponse.getDescription() : "");
        variables.put("ruleTitle", BaseResponse.getTitle() != null ? BaseResponse.getTitle() : "");
        variables.put("triggeredAt", BaseResponse.getTriggeredAt() != null ? BaseResponse.getTriggeredAt().toString() : "");
        variables.put("traceId", context.getTraceId());
        variables.put("tenantId", context.getTenantId());
        // 透传 facts 中的关键字段
        Object projectCode = context.get("projectCode");
        if (projectCode != null) {
            variables.put("projectCode", projectCode.toString());
        }
        body.put("variables", variables);

        return body;
    }

    /**
     * 从规则结果或上下文中解析工作流流程定义 Key
     *
     * <p>解析优先级：
     * <ol>
     *   <li>RuleResult.scope 字段，格式为 "workflow:{processKey}"</li>
     *   <li>RuleContext facts 中的 "workflowProcessKey" 键</li>
     *   <li>RuleContext facts 中的 "workflow" 嵌套 Map 的 "processKey" 键</li>
     * </ol>
     *
     * @param result  规则结果
     * @param context 规则上下文
     * @return 流程定义 Key；未找到返回 null
     */
    @SuppressWarnings("unchecked")
    private String resolveProcessKey(RuleResult result, RuleContext context) {
        // 1. 从 scope 字段解析 "workflow:{processKey}"
        String scope = BaseResponse.getScope();
        if (scope != null && scope.startsWith("workflow:")) {
            String processKey = scope.substring("workflow:".length()).trim();
            if (!processKey.isEmpty()) {
                return processKey;
            }
        }

        // 2. 从 facts 中直接获取 "workflowProcessKey"
        Object directKey = context.get("workflowProcessKey");
        if (directKey != null && !directKey.toString().isBlank()) {
            return directKey.toString().trim();
        }

        // 3. 从 facts 中的嵌套 "workflow" Map 获取 "processKey"
        Object workflowConfig = context.get("workflow");
        if (workflowConfig instanceof Map) {
            Object nestedKey = ((Map<String, Object>) workflowConfig).get("processKey");
            if (nestedKey != null && !nestedKey.toString().isBlank()) {
                return nestedKey.toString().trim();
            }
        }

        return null;
    }
}
