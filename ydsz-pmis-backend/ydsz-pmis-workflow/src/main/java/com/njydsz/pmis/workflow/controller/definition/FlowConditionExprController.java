package com.njydsz.pmis.workflow.controller.definition;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.service.definition.FlowConditionExprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 条件表达式可视化编辑器 Controller（P2-1）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/condition-expr")
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
    @Idempotent(key = "flow-condition-expr:build", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/build")
    @Operation(summary = "结构化条件 JSON → 表达式字符串")
    public Result<String> build(@RequestBody Map<String, String> body) {
        String conditionJson = body.get("conditionJson");
        String engine = body.getOrDefault("engine", "AVIATOR");
        return Result.ok(conditionExprService.buildExpression(conditionJson, engine));
    }

    /**
     * 表达式字符串转结构化条件 JSON。
     *
     * @param body 请求体，需包含 expression 和可选的 engine（默认 AVIATOR）
     * @return 转换后的结构化条件 JSON 字符串
     */
    @Idempotent(key = "flow-condition-expr:parse", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/parse")
    @Operation(summary = "表达式字符串 → 结构化条件 JSON")
    public Result<String> parse(@RequestBody Map<String, String> body) {
        String expression = body.get("expression");
        String engine = body.getOrDefault("engine", "AVIATOR");
        return Result.ok(conditionExprService.parseExpression(expression, engine));
    }

    /**
     * 校验表达式语法。
     *
     * @param body 请求体，需包含 expression 和可选的 engine（默认 AVIATOR）
     * @return 校验结果（valid / errors 等字段）
     */
    @Idempotent(key = "flow-condition-expr:validate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/validate")
    @Operation(summary = "校验表达式语法")
    public Result<Map<String, Object>> validate(@RequestBody Map<String, String> body) {
        String expression = body.get("expression");
        String engine = body.getOrDefault("engine", "AVIATOR");
        return Result.ok(conditionExprService.validateExpression(expression, engine));
    }

    /**
     * 获取可用的操作符列表。
     *
     * @return 操作符列表
     */
    @GetMapping("/operators")
    @Operation(summary = "获取可用的操作符列表")
    public Result<List<Map<String, String>>> operators() {
        return Result.ok(conditionExprService.getOperators());
    }

    /**
     * 获取可用的值类型列表。
     *
     * @return 值类型列表
     */
    @GetMapping("/value-types")
    @Operation(summary = "获取可用的值类型列表")
    public Result<List<Map<String, String>>> valueTypes() {
        return Result.ok(conditionExprService.getValueTypes());
    }
}
