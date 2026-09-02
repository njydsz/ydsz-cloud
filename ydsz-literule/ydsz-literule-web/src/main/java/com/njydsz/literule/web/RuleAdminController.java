package com.njydsz.literule.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.dto.ExpressionValidateDTO;
import com.njydsz.literule.domain.dto.RuleABTestDTO;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleVersionDiffVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.server.config.ABTestService;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.expression.ExpressionValidationService;
import com.njydsz.literule.server.version.RuleVersionDiffService;

/**
 * 规则管理核心 Controller
 *
 * <p>提供规则 CRUD、启停、版本管理、dry-run 仿真、表达式校验、A/B 测试、 引擎执行统计等核心 REST API。
 *
 * <p>1.6.0 起从 project 模块迁移至 literule 模块，通过 SPI 接口反转依赖， 避免 literule 直接依赖 project 模块。
 *
 * <p>26.09.01 重构：原"胖 Controller"按功能域拆分为 14 个 Controller，本类只保留 核心规则 CRUD + 版本管理 + 表达式校验 + dry-run +
 * stats + abTest。 其他能力拆分至同包下的以下 Controller（共享基路径 {@code /ruleEngine/rules}）：
 *
 * <ul>
 *   <li>{@link RuleTemplateController} - 规则模板市场
 *   <li>{@link RuleTestCaseController} - 测试用例管理
 *   <li>{@link RuleLifecycleController} - 生命周期管理与多级审批流
 *   <li>{@link RuleTraceController} - 执行链路追踪与回放
 *   <li>{@link RuleDecisionTableController} - 决策表管理
 *   <li>{@link RuleImportExportController} - 规则导入导出
 *   <li>{@link RuleBatchController} - 批量操作与软删除
 *   <li>{@link RuleGraphController} - 规则链画布与表达式预览
 *   <li>{@link RuleDependencyController} - 规则依赖
 *   <li>{@link RuleCategoryController} - 规则目录树与责任人
 *   <li>{@link RuleABPolicyController} - AB Test 自动回滚策略
 *   <li>{@link RulePackController} - 规则集市场与压测
 *   <li>{@link RuleConflictController} - 规则冲突检测
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则引擎管理", description = "规则 CRUD、版本、dry-run、冲突检测、画布、模板市场、规则集市场")
public class RuleAdminController {

  /** 规则管理服务 */
  private final RuleAdminService ruleAdminService;

  /** A/B 测试服务 */
  private final ABTestService abTestService;

  /** 规则引擎 */
  private final RuleEngine ruleEngine;

  /** 表达式校验服务 */
  private final ExpressionValidationService expressionValidationService;

  /** 版本 Diff 服务 */
  private final RuleVersionDiffService ruleVersionDiffService;

  /**
   * 分页查询规则定义（P1-2 分页标准化）
   *
   * @param pageQuery 分页查询参数（pageNum / pageSize / orderItems）
   * @return 分页规则定义列表
   * @since 26.09.01
   */
  @Operation(summary = "分页查询规则定义", description = "分页查询规则引擎中所有规则定义，支持按页码、页大小、排序字段进行分页")
  @ApiResponse(responseCode = "200", description = "分页规则定义列表")
  @GetMapping
  public PageResponse<List<RuleDefinitionVO>> list(
      PageQuery pageQuery) {
    PageResponse<List<RuleDefinitionDTO>> page =
        ruleAdminService.pageRuleDefinitions(pageQuery);
    List<RuleDefinitionVO> records =
        page.getData().stream().map(LiteruleWebConverter.INSTANCE::entityToVO).toList();
    return PageResponse.success(
        page.getTotal(), page.getPageNum(), page.getPageSize(), records);
  }

  /**
   * 查询单条规则定义
   *
   * @param ruleCode 规则编码
   * @return 规则定义
   */
  @Operation(summary = "查询单条规则定义", description = "根据规则编码查询单条规则定义的详细信息")
  @Parameter(name = "ruleCode", description = "规则编码", required = true)
  @ApiResponse(responseCode = "200", description = "规则定义详情")
  @GetMapping("/{ruleCode}")
  public YdszResponse<RuleDefinitionVO> get(@PathVariable String ruleCode) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(ruleAdminService.getByCode(ruleCode)));
  }

  /**
   * 新增/更新规则
   *
   * @param definition 规则定义
   * @param operator 操作人（从 Header 获取）
   * @param changeDesc 变更描述
   * @return 保存后的规则定义
   */
  @Operation(summary = "新增/更新规则", description = "创建新规则或更新已有规则的定义（条件表达式、严重度、优先级等）")
  @Parameter(name = "X-Operator", description = "操作人（Header）", required = false)
  @Parameter(name = "changeDesc", description = "变更描述", required = false)
  @ApiResponse(responseCode = "200", description = "保存后的规则定义")
  @Idempotent(key = "ruleAdmin:save", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'规则保存: ' + #definition.code + ', 操作人: ' + #operator")
  @RateLimit(resource = "literule.rule_admin.save", threshold = 50)
  @PostMapping
  @AuthApiPermission(apiCodes = "execution:rule:save")
  public YdszResponse<RuleDefinitionVO> save(
      @RequestBody RuleDefinitionDTO definition,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator,
      @RequestParam(value = "changeDesc", defaultValue = "API 更新") String changeDesc) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(
            ruleAdminService.save(definition, operator, changeDesc)));
  }

  /**
   * 切换规则启停
   *
   * @param ruleCode 规则编码
   * @param enabled 是否启用
   * @param operator 操作人
   * @return 操作结果
   */
  @Operation(summary = "切换规则启停", description = "启用或禁用指定编码的规则")
  @Parameter(name = "ruleCode", description = "规则编码", required = true)
  @ApiResponse(responseCode = "200", description = "操作成功")
  @Idempotent(key = "ruleAdmin:toggle", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'规则启停切换: ' + #ruleCode + ', 启用: ' + #enabled + ', 操作人: ' + #operator")
  @RateLimit(resource = "literule.rule_admin.toggle", threshold = 50)
  @PutMapping("/{ruleCode}/toggle")
  @AuthApiPermission(apiCodes = "execution:rule:toggle")
  public YdszResponse<Void> toggle(
      @PathVariable String ruleCode,
      @RequestParam boolean enabled,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    ruleAdminService.toggle(ruleCode, enabled, operator);
    return YdszResponse.success();
  }

  /**
   * 分页查询规则版本历史
   *
   * <p>支持分页，避免全量加载大量版本记录导致 OOM。{@code pageNum}/{pageSize} 均为可选，默认第 1 页、每页 20 条。
   *
   * @param ruleCode 规则编码
   * @param pageQuery 分页参数
   * @return 分页版本历史
   */
  @Operation(summary = "分页查询规则版本历史", description = "分页查询规则的版本历史记录，按版本号降序")
  @Parameter(name = "ruleCode", description = "规则编码", required = true)
  @Parameter(name = "pageNum", description = "页码（默认 1）", required = false)
  @Parameter(name = "pageSize", description = "每页条数（默认 20，最大 100）", required = false)
  @ApiResponse(responseCode = "200", description = "分页版本历史")
  @GetMapping("/{ruleCode}/versions")
  public PageResponse<List<RuleVersionVO>> listVersions(
      @PathVariable String ruleCode, @Valid PageQuery pageQuery) {
    return ruleAdminService.pageVersions(ruleCode, pageQuery);
  }

  /**
   * 版本 Diff（结构化对比两个版本的定义差异）
   *
   * <p>对指定规则的两个版本进行字段级结构化对比，产出变更项列表。 前端可据此高亮具体变更字段，并渲染 diff 视图。
   *
   * @param ruleCode 规则编码
   * @param oldVersion 旧版本号
   * @param newVersion 新版本号
   * @return 结构化 Diff 结果
   * @since 26.09.01
   */
  @GetMapping("/{ruleCode}/version-diff")
  public YdszResponse<RuleVersionDiffVO> versionDiff(
      @PathVariable String ruleCode, @RequestParam int oldVersion, @RequestParam int newVersion) {
    List<RuleVersionVO> versions = ruleAdminService.listVersions(ruleCode);
    RuleVersionVO oldV =
        versions.stream().filter(v -> v.getVersion() == oldVersion).findFirst().orElse(null);
    RuleVersionVO newV =
        versions.stream().filter(v -> v.getVersion() == newVersion).findFirst().orElse(null);

    if (oldV == null || newV == null) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_VERSION_NOT_FOUND,
          "版本不存在: oldVersion=" + oldVersion + ", newVersion=" + newVersion);
    }

    try {
      RuleDefinitionDTO oldDef = YdszJson.fromJson(oldV.getDefinitionJson(), RuleDefinitionDTO.class);
      RuleDefinitionDTO newDef = YdszJson.fromJson(newV.getDefinitionJson(), RuleDefinitionDTO.class);
      return YdszResponse.success(
          LiteruleWebConverter.INSTANCE.entityToVO(ruleVersionDiffService.diff(oldDef, newDef)));
    } catch (Exception e) {
      log.error(
          "[LiteRule] 版本 Diff 失败: ruleCode={}, oldV={}, newV={}",
          ruleCode,
          oldVersion,
          newVersion,
          e);
      return YdszResponse.error(
          YdszResultCode.VALIDATION_FAILED, "版本 Diff 解析失败: " + e.getMessage());
    }
  }

  /**
   * 回滚到指定版本
   *
   * @param ruleCode 规则编码
   * @param version 目标版本号
   * @param operator 操作人
   * @return 回滚后的规则定义
   */
  @Idempotent(key = "ruleAdmin:rollback", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'规则回滚: ' + #ruleCode + ', 目标版本: ' + #version + ', 操作人: ' + #operator")
  @RateLimit(resource = "literule.rule_admin.rollback", threshold = 50)
  @PostMapping("/{ruleCode}/rollback")
  public YdszResponse<RuleDefinitionVO> rollback(
      @PathVariable String ruleCode,
      @RequestParam int version,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleDefinitionVO restored =
        ruleAdminService
            .rollback(ruleCode, version, operator)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "回滚失败: 版本不存在, ruleCode=" + ruleCode + ", version=" + version));
    return YdszResponse.success(restored);
  }

  /**
   * Dry-run 仿真
   *
   * @param ruleCode 规则编码（可选，null 仿真全部规则）
   * @param facts 事实数据
   * @return 仿真结果
   */
  @Idempotent(key = "ruleAdmin:dryRun", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'规则仿真运行: ' + (#ruleCode != null ? #ruleCode : 'ALL') + ', 操作人: SYSTEM")
  @RateLimit(resource = "literule.rule_admin.dryRun", threshold = 50)
  @PostMapping("/dry-run")
  public YdszResponse<List<RuleResultVO>> dryRun(
      @RequestParam(required = false) String ruleCode, @RequestBody Map<String, Object> facts) {
    return YdszResponse.success(
        ruleAdminService.dryRun(ruleCode, facts).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /**
   * 校验表达式语法
   *
   * @param expression 表达式
   * @return true=合法
   */
  @GetMapping("/validate")
  public YdszResponse<Boolean> validate(@RequestParam String expression) {
    return YdszResponse.success(ruleAdminService.validateExpression(expression));
  }

  /**
   * 规则评估（正式模式，P0-2 补建）
   *
   * <p>与 {@link #dryRun} 的区别：正式评估记录执行统计（评估次数/触发次数/耗时）、发布规则触发事件
   * （供消息中心等下游消费）并触发动作分发（如发送通知、调用接口）。
   *
   * <p>历史缺陷复盘：{@code FeignClientConstants.LITERULE_PATH_EVALUATE} 指向的
   * {@code /ruleEngine/rules/evaluate} 在后端不存在，Feign 调用 404 后静默走
   * {@code LiteRuleClientFallback}。本端点补齐正式评估的 HTTP 面，路径与
   * {@code LiteRuleClient#evaluate} 契约对齐。
   *
   * <p>设计说明：本端点为服务间高频调用入口，不启用 {@code @Idempotent}（避免批评估场景误拒），
   * 通过 {@code @RateLimit} 提供接口级限流防护；租户与链路 ID 从网关注入的请求头解析，
   * 与 {@code TenantContextFeignInterceptor} / 前端 {@code X-Tenant-Id} 注入约定对齐。
   *
   * @param ruleCode 规则编码（可选，null 时评估全部规则，命中结果按此过滤）
   * @param scenario 场景标识（可选，用于规则过滤和统计分组，缺省 DEFAULT）
   * @param tenantId 租户 ID（可选，取 X-Tenant-Id 请求头，缺省使用引擎默认租户）
   * @param traceId 链路追踪 ID（可选，取 X-Trace-Id 请求头）
   * @param facts 事实数据
   * @return 触发的规则结果列表（按严重度倒序），未触发任何规则时返回空列表
   * @since 26.09.01
   */
  @Operation(summary = "规则评估（正式模式）", description = "记录统计、发布触发事件并触发动作分发的正式规则评估")
  @ApiResponse(responseCode = "200", description = "触发的规则结果列表")
  @RateLimit(resource = "literule.rule_admin.evaluate", threshold = 50)
  @PostMapping("/evaluate")
  public YdszResponse<List<RuleResultVO>> evaluate(
      @RequestParam(required = false) String ruleCode,
      @RequestParam(required = false) String scenario,
      @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
      @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
      @RequestBody Map<String, Object> facts) {
    String scen = scenario != null ? scenario : "DEFAULT";
    RuleContextVO context =
        tenantId != null
            ? RuleContextVO.of(facts, scen, "HTTP", traceId, tenantId)
            : RuleContextVO.of(facts, scen, "HTTP", traceId);
    List<RuleResultVO> results = ruleEngine.evaluate(context);
    List<RuleResultVO> filtered =
        ruleCode == null
            ? results
            : results.stream().filter(r -> ruleCode.equals(r.getRuleCode())).toList();
    return YdszResponse.success(
        filtered.stream().map(LiteruleWebConverter.INSTANCE::entityToVO).toList());
  }

  /**
   * 表达式追踪求值（P0-2 表达式级追踪/归因）
   *
   * <p>将表达式执行过程转换为计算树， 用于规则归因分析、短路排查和中间结果可视化。
   *
   * <p>请求体示例：
   *
   * <pre>
   * POST /rules/expr-trace
   * {
   *   "expression": "amount > 1000 && score > 800",
   *   "facts": { "amount": 1500, "score": 750 }
   * }
   * </pre>
   *
   * @param request 包含 expression 和 facts 的请求体
   * @return 追踪结果（含求值结果和追踪树）
   * @since 26.09.01
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'表达式追踪: ' + #request['expression']")
  @RateLimit(resource = "literule.rule_admin.traceExpression", threshold = 50)
  @PostMapping("/expr-trace")
  public YdszResponse<ExpressionEngine.TraceResult> traceExpression(
      @RequestBody Map<String, Object> request) {
    String expression = (String) request.get("expression");
    Map<String, Object> facts = new HashMap<>(16);
    Object raw = request.get("facts");
    if (raw instanceof Map<?, ?> rawMap) {
      rawMap.forEach((k, v) -> facts.put(String.valueOf(k), v));
    }
    return YdszResponse.success(ruleAdminService.traceExpression(expression, facts));
  }

  /**
   * 详细校验条件表达式（1.4.0 起支持）
   *
   * <p>返回结构化的校验结果，包含错误类型、错误位置、错误描述、引用的变量列表， 供前端表达式编辑器渲染错误标记和自动补全提示。
   *
   * @param dto 表达式校验请求参数（包含待校验的表达式和表达式类型）
   * @return 校验结果
   */
  @Idempotent(key = "ruleAdmin:validateExpression", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'表达式校验: ' + #dto.expression + ', 类型: ' + #dto.type")
  @RateLimit(resource = "literule.rule_admin.validateExpression", threshold = 50)
  @PostMapping("/validate-expression")
  public YdszResponse<ExpressionValidationResultVO> validateExpression(
      @Valid @RequestBody ExpressionValidateDTO dto) {
    String expression = dto.getExpression();
    String type = dto.getType() == null ? "condition" : dto.getType();
    ExpressionValidationResult result;
    switch (type) {
      case "severity":
        result = expressionValidationService.validateSeverity(expression);
        break;
      case "template":
        result = expressionValidationService.validateTemplate(expression);
        break;
      case "condition":
      default:
        result = expressionValidationService.validateCondition(expression);
        break;
    }
    return YdszResponse.success(LiteruleWebConverter.INSTANCE.entityToVO(result));
  }

  /**
   * 批量校验表达式（1.4.0 起支持）
   *
   * @param request key=标签，value=表达式
   * @return 校验结果（与输入顺序一致）
   */
  @Idempotent(key = "ruleAdmin:validateBatch", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'批量表达式校验: 共 ' + #request.size() + ' 条'")
  @RateLimit(resource = "literule.rule_admin.validateBatch", threshold = 50)
  @PostMapping("/validate-batch")
  public YdszResponse<Map<String, ExpressionValidationResult>> validateBatch(
      @RequestBody Map<String, String> request) {
    return YdszResponse.success(expressionValidationService.validateBatch(request));
  }

  /**
   * 规则 A/B 测试
   *
   * <p>对同一事实数据分别评估当前规则版本和候选规则版本，返回对比报告。 用于规则变更前的安全验证。
   *
   * @param ruleCode 规则编码
   * @param dto A/B 测试请求参数（包含候选规则定义）
   * @return A/B 测试报告
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'规则AB测试: ' + #ruleCode + ', 操作人: SYSTEM")
  @RateLimit(resource = "literule.rule_admin.abTest", threshold = 50)
  @PostMapping("/{ruleCode}/ab-test")
  public YdszResponse<ABTestService.ABTestReport> abTest(
      @PathVariable String ruleCode, @Valid @RequestBody RuleABTestDTO dto) {
    RuleDefinitionDTO currentDef = ruleAdminService.getByCode(ruleCode);
    if (currentDef == null) {
      return YdszResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
    }

    // 构建候选规则定义（基于当前规则，覆盖候选字段）
    RuleDefinitionDTO candidateDef = dto.getCandidate();
    candidateDef.setCode(ruleCode);

    return YdszResponse.success(abTestService.test(currentDef, candidateDef, dto.getFacts()));
  }

  /**
   * 查询规则引擎执行统计
   *
   * @return 统计快照
   */
  @GetMapping("/stats")
  public YdszResponse<RuleEngineStatsVO> stats() {
    return YdszResponse.success(LiteruleWebConverter.INSTANCE.entityToVO(ruleEngine.getStats()));
  }
}

