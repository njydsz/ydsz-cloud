package com.njydsz.workflow.web.controller.definition;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import com.njydsz.workflow.server.engine.JsonHelper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 条件表达式可视化编辑器 Controller（P2-1）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/conditionExpr")
@RequiredArgsConstructor
@Tag(name = "条件表达式编辑器", description = "结构化条件 JSON ↔ 表达式字符串双向转换")
public class FlowConditionExprController {

    /** 条件表达式服务，负责结构化条件 JSON 与表达式字符串的双向转换与校验 */
    private final FlowConditionExprService conditionExprService;

    /**
     * 结构化条件 JSON 转表达式字符串。
     *
     * @param body 请求体，需包含 conditionJson 和可选的 engine（默认 AVIATOR）
     * @return 转换后的表达式字符串
     */
    @Idempotent(key = "ydsz:workflow:FlowConditionExprController:build:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowconditionexpr.build", threshold = 50)
    @PostMapping("/build")
    @Operation(summary = "结构化条件 JSON → 表达式字符串")
    public BaseResponse<String> build(@RequestBody Map<String, String> body) {
        String conditionJson = body.get("conditionJson");
        String engine = body.getOrDefault("engine", "AVIATOR");
        return BaseResponse.success(conditionExprService.buildExpression(conditionJson, engine));
    }

    /**
     * 表达式字符串转结构化条件 JSON。
     *
     * @param body 请求体，需包含 expression 和可选的 engine（默认 AVIATOR）
     * @return 转换后的结构化条件 JSON 字符串
     */
    @Idempotent(key = "ydsz:workflow:FlowConditionExprController:parse:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowconditionexpr.parse", threshold = 50)
    @PostMapping("/parse")
    @Operation(summary = "表达式字符串 → 结构化条件 JSON")
    public BaseResponse<String> parse(@RequestBody Map<String, String> body) {
        String expression = body.get("expression");
        String engine = body.getOrDefault("engine", "AVIATOR");
        return BaseResponse.success(conditionExprService.parseExpression(expression, engine));
    }

    /**
     * 校验表达式语法。
     *
     * @param body 请求体，需包含 expression 和可选的 engine（默认 AVIATOR）
     * @return 校验结果（valid / errors 等字段）
     */
    @Idempotent(key = "ydsz:workflow:FlowConditionExprController:validate:lock", ttlSeconds = 5)
    @PostMapping("/validate")
    @Operation(summary = "校验表达式语法")
    public BaseResponse<Map<String, Object>> validate(@RequestBody Map<String, String> body) {
        String expression = body.get("expression");
        String engine = body.getOrDefault("engine", "AVIATOR");
        return BaseResponse.success(conditionExprService.validateExpression(expression, engine));
    }

    /**
     * 获取可用的操作符列表。
     *
     * @return 操作符列表
     */
    @GetMapping("/operators")
    @Operation(summary = "获取可用的操作符列表")
    public BaseResponse<List<Map<String, String>>> operators() {
        return BaseResponse.success(conditionExprService.getOperators());
    }

    /**
     * 获取可用的值类型列表。
     *
     * @return 值类型列表
     */
    @GetMapping("/valueTypes")
    @Operation(summary = "获取可用的值类型列表")
    public BaseResponse<List<Map<String, String>>> valueTypes() {
        return BaseResponse.success(conditionExprService.getValueTypes());
    }

    // ==================== P1-4: 可视化编辑增强 API ====================

    /**
     * 获取指定流程定义的可用变量列表。
     *
     * @param definitionId 流程定义 ID
     * @return 变量列表
     */
    @GetMapping("/variables/{definitionId}")
    @Operation(summary = "获取流程定义的可用变量列表")
    public BaseResponse<List<Map<String, String>>> variables(@PathVariable String definitionId) {
        return BaseResponse.success(conditionExprService.getVariablesByDefinition(definitionId));
    }

    /**
     * 预览表达式执行结果。
     *
     * @param body 请求体，需包含 expression、variables、可选的 engine
     * @return 执行结果
     */
    @PostMapping("/preview")
    @Operation(summary = "预览表达式执行结果")
    public BaseResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        String expression = body.get("expression") instanceof String s ? s : null;
        String engine = body.get("engine") instanceof String s ? s : "AVIATOR";
        Map<String, Object> variables = body.get("variables") instanceof Map<?, ?> m
                ? JsonHelper.toStringObjectMap(m) : Map.of();
        return BaseResponse.success(conditionExprService.previewExpression(expression, variables, engine));
    }

    /**
     * 获取条件模板列表。
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    @Operation(summary = "获取条件模板列表")
    public BaseResponse<List<Map<String, String>>> templates() {
        return BaseResponse.success(conditionExprService.getConditionTemplates());
    }
}
