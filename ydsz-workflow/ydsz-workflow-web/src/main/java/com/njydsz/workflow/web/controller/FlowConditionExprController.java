package com.njydsz.workflow.web.controller.definition;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 条件表达式可视化编辑器 Controller（P2-1 / P1-4）
 *
 * <p>提供流程网关条件表达式的<b>可视化编辑与调试</b>能力。流程设计器中 ExclusiveGateway / InclusiveGateway 的跳转条件既可由后端生成（基于结构化
 * JSON）， 也可由业务方手动编写（基于表达式字符串），本 Controller 在两种表达形式之间架起桥梁。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/conditionExpr/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>结构化 ↔ 表达式</b>：{@code POST /build} 结构化 JSON → 表达式字符串； {@code POST /parse} 表达式字符串 → 结构化
 *       JSON（前端可视化编辑器双向同步）
 *   <li><b>语法校验</b>：{@code POST /validate} 校验表达式语法，返回结构化错误位置
 *   <li><b>元数据查询</b>：{@code GET /operators} 操作符列表 / {@code GET /valueTypes} 值类型列表 / {@code GET
 *       /templates} 条件模板 / {@code GET /variables/{definitionId}} 流程可用变量
 *   <li><b>执行预览</b>：{@code POST /preview} 在前端编辑器中实时预览表达式执行结果
 * </ul>
 *
 * <p><b>支持的表达式引擎：</b>AVIATOR（默认，高性能轻量级求值器，{@code ydsz.workflow.engine.condition.default=AVIATOR}）、
 * GROOVY（动态脚本，重量级但表达力强，需开启 {@code ydsz.workflow.engine.condition.allow-groovy=true}）。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（build/parse/validate）启用 {@link Idempotent} 5s 防重
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>表达式在 Service 层使用白名单操作符 / 函数，禁止运行时 new Java 对象 / 调用任意方法
 *   <li>GROOVY 引擎需在配置中显式开启，避免被误用为代码执行沙箱
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.FlowConditionExprService 条件表达式服务
 * @see com.njydsz.workflow.server.engine.JsonHelper JSON 转换助手
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow/conditionExpr")
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
  @Audit(
      module = "条件表达式",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'build'")
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
  @Audit(
      module = "条件表达式",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'parse'")
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
    Map<String, Object> variables =
        body.get("variables") instanceof Map<?, ?> m ? MapUtils.toStringObjectMap(m) : Map.of();
    return BaseResponse.success(
        conditionExprService.previewExpression(expression, variables, engine));
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
