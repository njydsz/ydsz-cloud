package com.njydsz.workflow.web.controller.dmn;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.workflow.domain.entity.FlowDmnDecision;
import com.njydsz.workflow.domain.entity.FlowDmnRule;
import com.njydsz.workflow.server.service.FlowDmnDecisionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowDmnDecisionVO;

/**
 * DMN 决策表 Controller（P0-1）
 *
 * <p>提供决策表（DMN 1.3 兼容实现）的<b>全生命周期管理</b>与<b>运行时评估</b> RESTful API，
 * 是工作流「决策中心」与业务侧规则路由的核心入口。DMN 决策表常用于：
 * <ul>
 *   <li><b>网关路由</b>：与 BPMN ExclusiveGateway / InclusiveGateway 绑定，规则命中后决定流程走向</li>
 *   <li><b>审批人指派</b>：与 UserTask assignmentValue 绑定，规则命中后动态选择审批人</li>
 *   <li><b>业务规则计算</b>：独立于流程使用，由业务方通过 {@code /evaluate} 直接调用</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/dmn/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@code POST /decision} 创建 / {@code PUT /decision/{id}} 更新（仅 DRAFT）</li>
 *   <li><b>状态流转</b>：{@code POST /decision/{id}/publish} 发布 / {@code POST /decision/{id}/deprecate} 停用</li>
 *   <li><b>查询</b>：{@code GET /decision/{id}} 详情 / {@code GET /decisions} 列表</li>
 *   <li><b>评估</b>：{@code POST /evaluate} 按编码评估 / {@code POST /evaluateByNode} 按流程节点评估</li>
 * </ul>
 *
 * <p><b>状态机：</b>{@code DRAFT}（可编辑）→ {@code PUBLISHED}（运行中，不可改规则）→ {@code DEPRECATED}（已停用，新评估失败但历史引用保留）。
 *
 * <p><b>多租户隔离：</b>所有读写按 {@link AuthContext#getTenantIdOrDefault} 隔离；
 * 跨租户决策表不可见，确保 SaaS 化部署数据安全。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重（避免双击重复发布）</li>
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流（防止管理后台高频操作拖垮评估引擎）</li>
 *   <li>更新仅允许 DRAFT 状态；PUBLISHED 状态更新会被 Service 层拒绝（避免线上规则被意外覆盖）</li>
 * </ul>
 *
 * <p><b>性能优化：</b>已发布决策表走 Redis 缓存（{@code ydsz:dmn:decision:published:{tenantId}:{code}}），
 * TTL 30min；评估命中缓存 O(1)，未命中回源 DB。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDmnDecisionService 决策表服务
 * @see FlowDmnDecision 决策表实体
 * @see FlowDmnRule 决策表规则实体
 */
@Slf4j
@RestController
@Tag(name = "workflow-dmn", description = "DMN 决策表引擎接口")
@RequestMapping("/api/v1/workflow/dmn")
@RequiredArgsConstructor
public class FlowDmnDecisionController {

    /** 决策表服务，负责 CRUD、发布、停用与评估 */
    private final FlowDmnDecisionService dmnDecisionService;

    /**
     * 创建决策表（含规则明细）
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>创建后状态默认为 {@code DRAFT}，需要调用 {@link #publish(String)} 才会被评估引擎使用。
     * <p>租户 ID 自动从 SecurityContext 注入到决策表实体。
     *
     * @param request 决策表创建请求（含 decision 元数据 + rules 规则列表）
     * @return 新创建的决策表 ID
     */
    @RateLimit(resource = "workflow.flowdmndecision.createDecision", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowDmnDecisionController:createDecision:lock", ttlSeconds = 5)
    @PostMapping("/decision")
    @Operation(summary = "创建决策表")
    public BaseResponse<String> createDecision(@RequestBody CreateDecisionRequest request) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        request.getDecision().setTenantId(tenantId);
        String id = dmnDecisionService.createDecision(request.getDecision(), request.getRules());
        return BaseResponse.success(id);
    }

    /**
     * 更新决策表（仅 DRAFT 状态可更新）
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>覆盖式更新：先删除旧规则，再插入新规则。PUBLISHED / DEPRECATED 状态调用此接口会失败。
     * <p>如需修改已发布的决策表，请先创建新版或调用 {@link #deprecate(String)} 停用旧版。
     *
     * @param decisionId 决策表 ID
     * @param request    决策表更新请求（覆盖 decision + rules）
     * @return 空响应
     */
    @RateLimit(resource = "workflow.flowdmndecision.updateDecision", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowDmnDecisionController:updateDecision:lock", ttlSeconds = 5)
    @PutMapping("/decision/{decisionId}")
    @Operation(summary = "更新决策表（仅草稿状态）")
    public BaseResponse<Void> updateDecision(@PathVariable String decisionId,
                                        @RequestBody CreateDecisionRequest request) {
        request.getDecision().setTenantId(AuthContext.getTenantIdOrDefault("1"));
        dmnDecisionService.updateDecision(decisionId, request.getDecision(), request.getRules());
        return BaseResponse.success();
    }

    /**
     * 发布决策表（DRAFT → PUBLISHED）
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>发布后决策表进入评估引擎缓存，可被 {@link #evaluate} 与 {@link #evaluateByNode} 调用。
     * <p>触发缓存预热（异步），同时清理旧版本缓存。
     *
     * @param decisionId 决策表 ID
     * @return 空响应
     */
    @RateLimit(resource = "workflow.flowdmndecision.publish", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowDmnDecisionController:publish:lock", ttlSeconds = 5)
    @PostMapping("/decision/{decisionId}/publish")
    @Operation(summary = "发布决策表")
    public BaseResponse<Void> publish(@PathVariable String decisionId) {
        dmnDecisionService.publish(decisionId);
        return BaseResponse.success();
    }

    /**
     * 停用决策表（PUBLISHED → DEPRECATED）
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>停用后评估引擎拒绝新调用，但历史流程实例对已绑定的决策表引用保留（避免数据不一致）。
     * <p>同步清除 Redis 缓存中的决策表条目。
     *
     * @param decisionId 决策表 ID
     * @return 空响应
     */
    @RateLimit(resource = "workflow.flowdmndecision.deprecate", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowDmnDecisionController:deprecate:lock", ttlSeconds = 5)
    @PostMapping("/decision/{decisionId}/deprecate")
    @Operation(summary = "停用决策表")
    public BaseResponse<Void> deprecate(@PathVariable String decisionId) {
        dmnDecisionService.deprecate(decisionId);
        return BaseResponse.success();
    }

    /**
     * 查询决策表详情（含规则明细）
     *
     * <p>返回决策表元数据 + 全部规则列表，用于设计器回显或编辑。
     * <p>查询不区分状态（DRAFT/PUBLISHED/DEPRECATED 均可查询）。
     *
     * @param decisionId 决策表 ID
     * @return 详情 Map（含 decision 元数据 + rules 规则列表）
     */
    @GetMapping("/decision/{decisionId}")
    @Operation(summary = "查询决策表详情（含规则）")
    public BaseResponse<Map<String, Object>> getDetail(@PathVariable String decisionId) {
        return BaseResponse.success(dmnDecisionService.getDetail(decisionId));
    }

    /**
     * 分页查询决策表列表（按租户隔离）
     *
     * <p>支持按决策表编码模糊查询（{@code decisionCode} 可空，空则返回租户下全部）。
     * <p>返回精简的决策表元数据列表（不含规则明细），用于决策中心列表展示。
     *
     * @param decisionCode 决策表编码（可选，支持模糊匹配）
     * @return 决策表列表 VO
     */
    @GetMapping("/decisions")
    @Operation(summary = "分页查询决策表列表")
    public BaseResponse<List<FlowDmnDecisionVO>> listDecisions(
            @RequestParam(required = false) String decisionCode) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowDmnDecisionListToVO(dmnDecisionService.listDecisions(decisionCode, tenantId)));
    }

    /**
     * 按决策表编码评估变量
     *
     * <p>运行时评估入口：传入变量，命中规则后返回评估结果。
     * <p>仅评估 PUBLISHED 状态的决策表；DRAFT / DEPRECATED 返回失败。
     * <p>命中后返回结果包含 {@code hit}（true/false）、{@code outputs}（输出变量）、{@code matchedRuleId}（命中的规则 ID）。
     *
     * @param request 评估请求（含 decisionCode + variables 输入变量）
     * @return 评估结果
     */
    @PostMapping("/evaluate")
    @Operation(summary = "评估决策表")
    public BaseResponse<Map<String, Object>> evaluate(@RequestBody EvaluateRequest request) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(dmnDecisionService.evaluate(
                request.getDecisionCode(), request.getVariables(), tenantId));
    }

    /**
     * 按流程 + 节点评估绑定的决策表
     *
     * <p>用于流程引擎在网关 / 审批人指派时调用：根据当前流程编码 + 节点编码查找绑定的决策表，
     * 传入流程变量评估，返回结果驱动流程推进。
     * <p>典型场景：BPMN ExclusiveGateway 配置了 {@code dmnRef}，流程推进到该网关时由引擎自动调用。
     *
     * @param request 评估请求（含 flowCode + nodeCode + variables）
     * @return 评估结果
     */
    @PostMapping("/evaluateByNode")
    @Operation(summary = "根据流程+节点评估绑定的决策表")
    public BaseResponse<Map<String, Object>> evaluateByNode(@RequestBody EvaluateByNodeRequest request) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(dmnDecisionService.evaluateByNode(
                request.getFlowCode(), request.getNodeCode(),
                request.getVariables(), tenantId));
    }

    // ============================== 请求/响应 DTO ==============================

    /**
     * 决策表创建/更新请求 DTO
     */
    @lombok.Data
    public static class CreateDecisionRequest {
        /** 决策表元数据 */
        private FlowDmnDecision decision;
        /** 决策表规则列表 */
        private List<FlowDmnRule> rules;
    }

    /**
     * 决策表评估请求 DTO
     */
    @lombok.Data
    public static class EvaluateRequest {
        /** 决策表编码 */
        private String decisionCode;
        /** 输入变量 Map */
        private Map<String, Object> variables;
    }

    /**
     * 按节点评估请求 DTO
     */
    @lombok.Data
    public static class EvaluateByNodeRequest {
        /** 流程编码 */
        private String flowCode;
        /** 节点编码 */
        private String nodeCode;
        /** 输入变量 Map */
        private Map<String, Object> variables;
    }
}
