package com.njydsz.workflow.web.controller.dmn;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.workflow.domain.entity.FlowDmnDecisionDO;
import com.njydsz.workflow.domain.entity.FlowDmnRuleDO;
import com.njydsz.workflow.server.service.FlowDmnDecisionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0-1: DMN 决策表 Controller
 *
 * <p>提供决策表的 CRUD、发布、评估 RESTful API。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-dmn", description = "DMN 决策表引擎接口")
@RequestMapping("/workflow/dmn")
@RequiredArgsConstructor
public class FlowDmnDecisionController {

    private final FlowDmnDecisionService dmnDecisionService;

    @PostMapping("/decision")
    @Operation(summary = "创建决策表")
    public BaseResponse<String> createDecision(@RequestBody CreateDecisionRequest request) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        request.getDecision().setTenantId(tenantId);
        String id = dmnDecisionService.createDecision(request.getDecision(), request.getRules());
        return BaseResponse.ok(id);
    }

    @PutMapping("/decision/{decisionId}")
    @Operation(summary = "更新决策表（仅草稿状态）")
    public BaseResponse<Void> updateDecision(@PathVariable String decisionId,
                                        @RequestBody CreateDecisionRequest request) {
        request.getDecision().setTenantId(AuthContext.getTenantIdOrDefault("1"));
        dmnDecisionService.updateDecision(decisionId, request.getDecision(), request.getRules());
        return BaseResponse.ok();
    }

    @PostMapping("/decision/{decisionId}/publish")
    @Operation(summary = "发布决策表")
    public BaseResponse<Void> publish(@PathVariable String decisionId) {
        dmnDecisionService.publish(decisionId);
        return BaseResponse.ok();
    }

    @PostMapping("/decision/{decisionId}/deprecate")
    @Operation(summary = "停用决策表")
    public BaseResponse<Void> deprecate(@PathVariable String decisionId) {
        dmnDecisionService.deprecate(decisionId);
        return BaseResponse.ok();
    }

    @GetMapping("/decision/{decisionId}")
    @Operation(summary = "查询决策表详情（含规则）")
    public BaseResponse<Map<String, Object>> getDetail(@PathVariable String decisionId) {
        return BaseResponse.ok(dmnDecisionService.getDetail(decisionId));
    }

    @GetMapping("/decisions")
    @Operation(summary = "分页查询决策表列表")
    public BaseResponse<List<FlowDmnDecisionDO>> listDecisions(
            @RequestParam(required = false) String decisionCode) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(dmnDecisionService.listDecisions(decisionCode, tenantId));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "评估决策表")
    public BaseResponse<Map<String, Object>> evaluate(@RequestBody EvaluateRequest request) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(dmnDecisionService.evaluate(
                request.getDecisionCode(), request.getVariables(), tenantId));
    }

    @PostMapping("/evaluateByNode")
    @Operation(summary = "根据流程+节点评估绑定的决策表")
    public BaseResponse<Map<String, Object>> evaluateByNode(@RequestBody EvaluateByNodeRequest request) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(dmnDecisionService.evaluateByNode(
                request.getFlowCode(), request.getNodeCode(),
                request.getVariables(), tenantId));
    }

    // ============================== 请求/响应 DTO ==============================

    @lombok.Data
    public static class CreateDecisionRequest {
        private FlowDmnDecisionDO decision;
        private List<FlowDmnRuleDO> rules;
    }

    @lombok.Data
    public static class EvaluateRequest {
        private String decisionCode;
        private Map<String, Object> variables;
    }

    @lombok.Data
    public static class EvaluateByNodeRequest {
        private String flowCode;
        private String nodeCode;
        private Map<String, Object> variables;
    }
}
