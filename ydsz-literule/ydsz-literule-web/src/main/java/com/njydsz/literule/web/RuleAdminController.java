package com.njydsz.literule.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.dto.ExpressionValidateDTO;
import com.njydsz.literule.api.dto.RuleABTestDTO;
import com.njydsz.literule.api.expression.ExpressionEngine;
import com.njydsz.literule.api.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleVersionDiffVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.server.config.ABTestService;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.expression.ExpressionValidationService;
import com.njydsz.literule.server.spi.RuleVersion;
import com.njydsz.literule.server.version.RuleVersionDiffService;

/**
 * 规则管理核心 Controller
 *
 * <p>提供规则 CRUD、启停、版本管理、dry-run 仿真、表达式校验、A/B 测试、
 * 引擎执行统计等核心 REST API。
 *
 * <p>1.6.0 起从 project 模块迁移至 literule 模块，通过 SPI 接口反转依赖，
 * 避免 literule 直接依赖 project 模块。
 *
 * <p>1.0.0 重构：原"胖 Controller"按功能域拆分为 14 个 Controller，本类只保留
 * 核心规则 CRUD + 版本管理 + 表达式校验 + dry-run + stats + abTest。
 * 其他能力拆分至同包下的以下 Controller（共享基路径 {@code /ruleEngine/rules}）：
 * <ul>
 *   <li>{@link RuleTemplateController} - 规则模板市场</li>
 *   <li>{@link RuleTestCaseController} - 测试用例管理</li>
 *   <li>{@link RuleLifecycleController} - 生命周期管理与多级审批流</li>
 *   <li>{@link RuleTraceController} - 执行链路追踪与回放</li>
 *   <li>{@link RuleDecisionTableController} - 决策表管理</li>
 *   <li>{@link RuleImportExportController} - 规则导入导出</li>
 *   <li>{@link RuleBatchController} - 批量操作与软删除</li>
 *   <li>{@link RuleGraphController} - 规则链画布与表达式预览</li>
 *   <li>{@link RuleDependencyController} - 规则依赖</li>
 *   <li>{@link RuleCategoryController} - 规则目录树与责任人</li>
 *   <li>{@link RuleABPolicyController} - AB Test 自动回滚策略</li>
 *   <li>{@link RulePackController} - 规则集市场与压测</li>
 *   <li>{@link RuleConflictController} - 规则冲突检测</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/rules")
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

    /**
     * 查询全部规则定义
     *
     * @return 规则定义列表
     */
    @GetMapping
    public BaseResponse<List<RuleDefinitionVO>> list() {
        return BaseResponse.success(ruleAdminService.listAll().stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
    }

    /**
     * 查询单条规则定义
     *
     * @param ruleCode 规则编码
     * @return 规则定义
     */
    @GetMapping("/{ruleCode}")
    public BaseResponse<RuleDefinitionVO> get(@PathVariable String ruleCode) {
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleAdminService.getByCode(ruleCode)));
    }

    /**
     * 新增/更新规则
     *
     * @param definition 规则定义
     * @param operator   操作人（从 Header 获取）
     * @param changeDesc 变更描述
     * @return 保存后的规则定义
     */
    @Idempotent(key = "ruleAdmin:save", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'save'")
    @RateLimit(resource = "literule.rule_admin.save", threshold = 50)
    @PostMapping
    @AuthApiPermission(apiCodes = "execution:rule:save")
    public BaseResponse<RuleDefinitionVO> save(@RequestBody RuleDefinition definition,
                                        @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator,
                                        @RequestParam(value = "changeDesc", defaultValue = "API 更新") String changeDesc) {
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleAdminService.save(definition, operator, changeDesc)));
    }

    /**
     * 切换规则启停
     *
     * @param ruleCode 规则编码
     * @param enabled  是否启用
     * @param operator 操作人
     * @return 操作结果
     */
    @Idempotent(key = "ruleAdmin:toggle", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'toggle'")
    @RateLimit(resource = "literule.rule_admin.toggle", threshold = 50)
    @PutMapping("/{ruleCode}/toggle")
    @AuthApiPermission(apiCodes = "execution:rule:toggle")
    public BaseResponse<Void> toggle(@PathVariable String ruleCode,
                                @RequestParam boolean enabled,
                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.toggle(ruleCode, enabled, operator);
        return BaseResponse.success();
    }

    /**
     * 查询规则版本历史
     *
     * @param ruleCode 规则编码
     * @return 版本历史
     */
    @GetMapping("/{ruleCode}/versions")
    public BaseResponse<List<RuleVersionVO>> listVersions(@PathVariable String ruleCode) {
        return BaseResponse.success(ruleAdminService.listVersions(ruleCode).stream().map(LiteruleWebConverter.INSTANT::entityToVO).toList());
    }

    /**
     * 版本 Diff（结构化对比两个版本的定义差异）
     *
     * <p>对指定规则的两个版本进行字段级结构化对比，产出变更项列表。
     * 前端可据此高亮具体变更字段，并渲染 diff 视图。
     *
     * @param ruleCode    规则编码
     * @param oldVersion  旧版本号
     * @param newVersion  新版本号
     * @return 结构化 Diff 结果
     * @since 1.0.0
     */
    @GetMapping("/{ruleCode}/versionDiff")
    public BaseResponse<RuleVersionDiffVO> versionDiff(@PathVariable String ruleCode,
                                                @RequestParam int oldVersion,
                                                @RequestParam int newVersion) {
        List<RuleVersion> versions = ruleAdminService.listVersions(ruleCode);
        RuleVersion oldV = versions.stream().filter(v -> v.getVersion() == oldVersion).findFirst().orElse(null);
        RuleVersion newV = versions.stream().filter(v -> v.getVersion() == newVersion).findFirst().orElse(null);

        if (oldV == null || newV == null) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_VERSION_NOT_FOUND, "版本不存在: oldVersion=" + oldVersion + ", newVersion=" + newVersion);
        }

        try {
            RuleDefinition oldDef = YdszJson.fromJson(oldV.getDefinitionJson(), RuleDefinition.class);
            RuleDefinition newDef = YdszJson.fromJson(newV.getDefinitionJson(), RuleDefinition.class);
            RuleVersionDiffService diffService = new RuleVersionDiffService();
            return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(diffService.diff(oldDef, newDef)));
        } catch (Exception e) {
            log.error("[LiteRule] 版本 Diff 失败: ruleCode={}, oldV={}, newV={}", ruleCode, oldVersion, newVersion, e);
            return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "版本 Diff 解析失败: " + e.getMessage());
        }
    }

    /**
     * 回滚到指定版本
     *
     * @param ruleCode 规则编码
     * @param version  目标版本号
     * @param operator 操作人
     * @return 回滚后的规则定义
     */
    @Idempotent(key = "ruleAdmin:rollback", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'rollback'")
    @RateLimit(resource = "literule.rule_admin.rollback", threshold = 50)
    @PostMapping("/{ruleCode}/rollback")
    public BaseResponse<RuleDefinitionVO> rollback(@PathVariable String ruleCode,
                                            @RequestParam int version,
                                            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleAdminService.rollback(ruleCode, version, operator)));
    }

    /**
     * Dry-run 仿真
     *
     * @param ruleCode 规则编码（可选，null 仿真全部规则）
     * @param facts    事实数据
     * @return 仿真结果
     */
    @Idempotent(key = "ruleAdmin:dryRun", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'dryRun'")
    @RateLimit(resource = "literule.rule_admin.dryRun", threshold = 50)
    @PostMapping("/dryRun")
    public BaseResponse<List<RuleResultVO>> dryRun(@RequestParam(required = false) String ruleCode,
                                            @RequestBody Map<String, Object> facts) {
        return BaseResponse.success(ruleAdminService.dryRun(ruleCode, facts).stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
    }

    /**
     * 校验表达式语法
     *
     * @param expression 表达式
     * @return true=合法
     */
    @GetMapping("/validate")
    public BaseResponse<Boolean> validate(@RequestParam String expression) {
        return BaseResponse.success(ruleAdminService.validateExpression(expression));
    }

    /**
     * 表达式追踪求值（P0-2 表达式级追踪/归因）
     *
     * <p>对标 QLExpress4 的 ExpressionTrace 能力，将表达式执行过程转换为计算树，
     * 用于规则归因分析、短路排查和中间结果可视化。
     *
     * <p>请求体示例：
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
     * @since 1.0.0
     */
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'traceExpression'")
    @RateLimit(resource = "literule.rule_admin.traceExpression", threshold = 50)
    @PostMapping("/exprTrace")
    public BaseResponse<ExpressionEngine.TraceResult> traceExpression(@RequestBody Map<String, Object> request) {
        String expression = (String) request.get("expression");
        Map<String, Object> facts = new HashMap<>();
        Object raw = request.get("facts");
        if (raw instanceof Map<?, ?> rawMap) {
            rawMap.forEach((k, v) -> facts.put(String.valueOf(k), v));
        }
        return BaseResponse.success(ruleAdminService.traceExpression(expression, facts));
    }

    /**
     * 详细校验条件表达式（1.4.0 起支持）
     *
     * <p>返回结构化的校验结果，包含错误类型、错误位置、错误描述、引用的变量列表，
     * 供前端表达式编辑器渲染错误标记和自动补全提示。
     *
     * @param expression 条件表达式
     * @return 校验结果
     */
    @Idempotent(key = "ruleAdmin:validateExpression", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'validateExpression'")
    @RateLimit(resource = "literule.rule_admin.validateExpression", threshold = 50)
    @PostMapping("/validateExpression")
    public BaseResponse<ExpressionValidationResultVO> validateExpression(@Valid @RequestBody ExpressionValidateDTO dto) {
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
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(result));
    }

    /**
     * 批量校验表达式（1.4.0 起支持）
     *
     * @param request key=标签，value=表达式
     * @return 校验结果（与输入顺序一致）
     */
    @Idempotent(key = "ruleAdmin:validateBatch", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @RateLimit(resource = "literule.rule_admin.validateBatch", threshold = 50)
    @PostMapping("/validateBatch")
    public BaseResponse<Map<String, ExpressionValidationResult>> validateBatch(@RequestBody Map<String, String> request) {
        return BaseResponse.success(expressionValidationService.validateBatch(request));
    }

    /**
     * 规则 A/B 测试
     *
     * <p>对同一事实数据分别评估当前规则版本和候选规则版本，返回对比报告。
     * 用于规则变更前的安全验证。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 candidate（候选规则定义）和 facts（事实数据）
     * @return A/B 测试报告
     */
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'abTest'")
    @RateLimit(resource = "literule.rule_admin.abTest", threshold = 50)
    @PostMapping("/{ruleCode}/abTest")
    public BaseResponse<ABTestService.ABTestReport> abTest(@PathVariable String ruleCode,
                                                      @Valid @RequestBody RuleABTestDTO dto) {
        RuleDefinition currentDef = ruleAdminService.getByCode(ruleCode);
        if (currentDef == null) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
        }

        // 构建候选规则定义（基于当前规则，覆盖候选字段）
        RuleDefinition candidateDef = dto.getCandidate();
        candidateDef.setCode(ruleCode);

        return BaseResponse.success(abTestService.test(currentDef, candidateDef, dto.getFacts()));
    }

    /**
     * 查询规则引擎执行统计
     *
     * @return 统计快照
     */
    @GetMapping("/stats")
    public BaseResponse<RuleEngineStatsVO> stats() {
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleEngine.getStats()));
    }
}
