package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.entity.DecisionTableDO;
import com.njydsz.pmis.project.entity.RuleExecutionTraceDO;
import com.njydsz.pmis.project.entity.RuleTemplateDO;
import com.njydsz.pmis.project.entity.RuleTestCaseDO;
import com.njydsz.pmis.project.literule.RuleConflictDetector;
import com.njydsz.pmis.project.literule.RuleGenerationService;
import com.njydsz.pmis.project.literule.RuleTemplateService;
import com.njydsz.pmis.project.mapper.DecisionTableMapper;
import com.njydsz.pmis.project.mapper.RuleTestCaseMapper;
import com.njydsz.pmis.project.mapper.RuleExecutionTraceMapper;
import com.njydsz.pmis.project.service.DecisionTableEvalService;
import com.njydsz.pmis.project.dto.ExpressionValidateDTO;
import com.njydsz.pmis.project.dto.RuleABTestDTO;
import com.njydsz.pmis.project.dto.RuleAiGenerateDTO;
import com.njydsz.pmis.project.dto.TestCaseBatchRunDTO;
import com.njydsz.pmis.project.dto.RuleStatusChangeDTO;
import com.njydsz.pmis.project.dto.RuleApproveDTO;
import com.njydsz.pmis.project.dto.RuleRejectDTO;
import com.njydsz.pmis.project.dto.RuleSubmitReviewDTO;
import com.njydsz.pmis.project.dto.RuleDelegateDTO;
import com.njydsz.pmis.project.dto.RuleImportDTO;
import com.njydsz.pmis.project.dto.RuleBatchToggleDTO;
import com.njydsz.pmis.project.dto.RuleBatchPriorityDTO;
import com.njydsz.pmis.project.dto.RuleBatchCategoryDTO;
import com.njydsz.pmis.project.dto.RuleDependencyAddDTO;
import com.njydsz.pmis.project.dto.RuleNL2RuleDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.config.ABTestService;
import com.njydsz.pmis.literule.config.DecisionTableAdminService;
import com.njydsz.pmis.literule.approval.ApprovalFlow;
import com.njydsz.pmis.literule.approval.ApprovalRecord;
import com.njydsz.pmis.literule.approval.RuleApprovalService;
import com.njydsz.pmis.literule.expr.ExpressionValidationResult;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionFunctionDef;
import com.njydsz.pmis.literule.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.orchestrator.RuleChainGraph;
import com.njydsz.pmis.literule.orchestrator.RuleGraphValidator;
import com.njydsz.pmis.literule.ai.RuleLLMService;
import com.njydsz.pmis.literule.ai.RuleHealthScore;
import com.njydsz.pmis.literule.ai.RuleHealthScoreService;
import com.njydsz.pmis.literule.ai.RuleRecommendation;
import com.njydsz.pmis.literule.ai.RuleRecommendationService;
import com.njydsz.pmis.literule.ai.RuleAttributionService;
import com.njydsz.pmis.literule.ai.AttributionReport;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.project.literule.RuleChainGraphService;
import com.njydsz.pmis.project.literule.RuleDependencyService;
import com.njydsz.pmis.project.literule.RuleCategoryTreeService;
import com.njydsz.pmis.project.literule.ABTestAutoRollbackService;
import com.njydsz.pmis.project.literule.RulePackService;
import com.njydsz.pmis.literule.benchmark.RuleStressTestService;
import com.njydsz.pmis.project.entity.RuleDependencyDO;
import com.njydsz.pmis.project.entity.RuleABPolicyDO;
import com.njydsz.pmis.project.entity.RuleABRollbackDO;
import com.njydsz.pmis.literule.api.RulePack;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 规则管理 Controller
 *
 * <p>提供规则 CRUD、启停、版本管理、dry-run 仿真、执行监控等 REST API。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@RestController
@RequestMapping("/execution/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则引擎管理", description = "规则 CRUD、版本、dry-run、冲突检测、画布、模板市场、AI 增强、规则集市场")
public class RuleAdminController {

    private final RuleAdminService ruleAdminService;
    private final ABTestService abTestService;
    private final RuleEngine ruleEngine;
    private final RuleTemplateService ruleTemplateService;
    private final RuleGenerationService ruleGenerationService;
    private final RuleConflictDetector ruleConflictDetector;
    private final RuleTestCaseMapper ruleTestCaseMapper;
    private final RuleExecutionTraceMapper ruleExecutionTraceMapper;
    private final DecisionTableMapper decisionTableMapper;
    private final ObjectMapper objectMapper;
    private final DecisionTableEvalService decisionTableEvalService;
    private final ExpressionValidationService expressionValidationService;
    private final RuleChainGraphService ruleChainGraphService;
    private final com.njydsz.pmis.project.literule.GraphExecutionService graphExecutionService;
    private final RuleDependencyService ruleDependencyService;
    private final RuleCategoryTreeService ruleCategoryTreeService;
    private final ABTestAutoRollbackService abTestAutoRollbackService;
    private final RulePackService rulePackService;
    // 规则压测服务（P2-9）：可选注入，RuleAdminService 未装配时为空
    private final org.springframework.beans.factory.ObjectProvider<RuleStressTestService> ruleStressTestServiceProvider;
    // 决策表管理服务（P0-3）：可选注入，未启用决策表时为空
    private final org.springframework.beans.factory.ObjectProvider<DecisionTableAdminService> decisionTableAdminServiceProvider;
    // AI 增强（P2-15）：可选注入，未启用 AI 时为空
    private final org.springframework.beans.factory.ObjectProvider<RuleLLMService> ruleLLMServiceProvider;
    private final org.springframework.beans.factory.ObjectProvider<RuleHealthScoreService> ruleHealthScoreServiceProvider;
    private final org.springframework.beans.factory.ObjectProvider<RuleRecommendationService> ruleRecommendationServiceProvider;
    // 归因分析服务（P3-3）：可选注入，未启用 AI 时为空
    private final org.springframework.beans.factory.ObjectProvider<RuleAttributionService> ruleAttributionServiceProvider;
    // 多级审批流服务（P1-3）：可选注入，未配置 RuleConfigProvider 时为空
    private final org.springframework.beans.factory.ObjectProvider<RuleApprovalService> ruleApprovalServiceProvider;

    /**
     * 查询全部规则定义
     *
     * @return 规则定义列表
     */
    @GetMapping
    public Result<List<RuleDefinition>> list() {
        return Result.ok(ruleAdminService.listAll());
    }

    /**
     * 查询单条规则定义
     *
     * @param ruleCode 规则编码
     * @return 规则定义
     */
    @GetMapping("/{ruleCode}")
    public Result<RuleDefinition> get(@PathVariable String ruleCode) {
        return Result.ok(ruleAdminService.getByCode(ruleCode));
    }

    /**
     * 新增/更新规则
     *
     * @param definition 规则定义
     * @param operator   操作人（从 Header 获取）
     * @param changeDesc 变更描述
     * @return 保存后的规则定义
     */
    @PostMapping
    @PrePermission("execution:rule:save")
    public Result<RuleDefinition> save(@RequestBody RuleDefinition definition,
                                        @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator,
                                        @RequestParam(value = "changeDesc", defaultValue = "API 更新") String changeDesc) {
        return Result.ok(ruleAdminService.save(definition, operator, changeDesc));
    }

    /**
     * 切换规则启停
     *
     * @param ruleCode 规则编码
     * @param enabled  是否启用
     * @param operator 操作人
     * @return 操作结果
     */
    @PutMapping("/{ruleCode}/toggle")
    @PrePermission("execution:rule:toggle")
    public Result<Void> toggle(@PathVariable String ruleCode,
                                @RequestParam boolean enabled,
                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.toggle(ruleCode, enabled, operator);
        return Result.ok();
    }

    /**
     * 查询规则版本历史
     *
     * @param ruleCode 规则编码
     * @return 版本历史
     */
    @GetMapping("/{ruleCode}/versions")
    public Result<List<RuleVersion>> listVersions(@PathVariable String ruleCode) {
        return Result.ok(ruleAdminService.listVersions(ruleCode));
    }

    /**
     * 回滚到指定版本
     *
     * @param ruleCode 规则编码
     * @param version  目标版本号
     * @param operator 操作人
     * @return 回滚后的规则定义
     */
    @PostMapping("/{ruleCode}/rollback")
    public Result<RuleDefinition> rollback(@PathVariable String ruleCode,
                                            @RequestParam int version,
                                            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return Result.ok(ruleAdminService.rollback(ruleCode, version, operator));
    }

    /**
     * Dry-run 仿真
     *
     * @param ruleCode 规则编码（可选，null 仿真全部规则）
     * @param facts    事实数据
     * @return 仿真结果
     */
    @PostMapping("/dry-run")
    public Result<List<RuleResult>> dryRun(@RequestParam(required = false) String ruleCode,
                                            @RequestBody Map<String, Object> facts) {
        return Result.ok(ruleAdminService.dryRun(ruleCode, facts));
    }

    /**
     * 校验表达式语法
     *
     * @param expression 表达式
     * @return true=合法
     */
    @GetMapping("/validate")
    public Result<Boolean> validate(@RequestParam String expression) {
        return Result.ok(ruleAdminService.validateExpression(expression));
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
     * @since 1.6.0
     */
    @PostMapping("/expr-trace")
    public Result<ExpressionEvaluator.TraceResult> traceExpression(@RequestBody Map<String, Object> request) {
        String expression = (String) request.get("expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) request.get("facts");
        return Result.ok(ruleAdminService.traceExpression(expression, facts));
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
    @PostMapping("/validate-expression")
    public Result<ExpressionValidationResult> validateExpression(@Valid @RequestBody ExpressionValidateDTO dto) {
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
        return Result.ok(result);
    }

    /**
     * 批量校验表达式（1.4.0 起支持）
     *
     * @param request key=标签，value=表达式
     * @return 校验结果（与输入顺序一致）
     */
    @PostMapping("/validate-batch")
    public Result<Map<String, ExpressionValidationResult>> validateBatch(@RequestBody Map<String, String> request) {
        return Result.ok(expressionValidationService.validateBatch(request));
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
    @PostMapping("/{ruleCode}/ab-test")
    public Result<ABTestService.ABTestReport> abTest(@PathVariable String ruleCode,
                                                      @Valid @RequestBody RuleABTestDTO dto) {
        RuleDefinition currentDef = ruleAdminService.getByCode(ruleCode);
        if (currentDef == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }

        // 构建候选规则定义（基于当前规则，覆盖候选字段）
        RuleDefinition candidateDef = dto.getCandidate();
        candidateDef.setCode(ruleCode);

        return Result.ok(abTestService.test(currentDef, candidateDef, dto.getFacts()));
    }

    /**
     * 查询规则引擎执行统计
     *
     * @return 统计快照
     */
    @GetMapping("/stats")
    public Result<RuleEngineStats> stats() {
        return Result.ok(ruleEngine.getStats());
    }

    // ==================== 规则模板市场 ====================

    /**
     * 查询全部规则模板
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    public Result<List<RuleTemplateDO>> listTemplates() {
        return Result.ok(ruleTemplateService.listAll());
    }

    /**
     * 按类别查询规则模板
     *
     * @param category 模板类别
     * @return 模板列表
     */
    @GetMapping("/templates/category/{category}")
    public Result<List<RuleTemplateDO>> listTemplatesByCategory(@PathVariable String category) {
        return Result.ok(ruleTemplateService.listByCategory(category));
    }

    /**
     * 按行业查询规则模板
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    @GetMapping("/templates/industry/{industry}")
    public Result<List<RuleTemplateDO>> listTemplatesByIndustry(@PathVariable String industry) {
        return Result.ok(ruleTemplateService.listByIndustry(industry));
    }

    /**
     * 一键导入模板为规则定义
     *
     * @param templateCode 模板编码
     * @param operator     操作人（从 Header 获取）
     * @return 保存后的规则定义
     */
    @PostMapping("/templates/{templateCode}/import")
    public Result<RuleDefinition> importTemplate(@PathVariable String templateCode,
                                                  @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return Result.ok(ruleTemplateService.importTemplate(templateCode, operator));
    }

    // ==================== AI 辅助规则生成 ====================

    /**
     * AI 辅助生成规则定义（仅生成建议，不保存）
     *
     * @param request 请求体，包含 description（自然语言描述）和 availableFields（可用字段列表）
     * @return 生成的规则定义
     */
    @PostMapping("/ai-generate")
    public Result<RuleDefinition> aiGenerate(@Valid @RequestBody RuleAiGenerateDTO dto) {
        List<String> fields = dto.getAvailableFields();
        if (fields == null) fields = List.of();
        return Result.ok(ruleGenerationService.generate(dto.getDescription(), fields));
    }

    /**
     * AI 辅助生成并保存规则定义
     *
     * @param request  请求体，包含 description（自然语言描述）和 availableFields（可用字段列表）
     * @param operator 操作人（从 Header 获取）
     * @return 保存后的规则定义
     */
    @PostMapping("/ai-generate-and-save")
    public Result<RuleDefinition> aiGenerateAndSave(@Valid @RequestBody RuleAiGenerateDTO dto,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> fields = dto.getAvailableFields();
        if (fields == null) fields = List.of();
        return Result.ok(ruleGenerationService.generateAndSave(dto.getDescription(), fields, operator));
    }

    // ==================== 冲突检测 ====================

    /**
     * 检测规则冲突
     *
     * @return 冲突规则对列表
     */
    @GetMapping("/conflicts")
    public Result<List<RuleConflictDetector.RuleConflictInfo>> detectConflicts() {
        return Result.ok(ruleConflictDetector.detectConflicts());
    }

    // ==================== 测试用例管理 ====================

    /**
     * 查询测试用例（可选按规则编码过滤）
     *
     * @param ruleCode 规则编码（可选）
     * @return 测试用例列表
     */
    @GetMapping("/test-cases")
    public Result<List<RuleTestCaseDO>> listTestCases(@RequestParam(required = false) String ruleCode) {
        LambdaQueryWrapper<RuleTestCaseDO> wrapper = new LambdaQueryWrapper<>();
        if (ruleCode != null && !ruleCode.isBlank()) {
            wrapper.eq(RuleTestCaseDO::getRuleCode, ruleCode);
        }
        wrapper.orderByDesc(RuleTestCaseDO::getUpdatedAt);
        return Result.ok(ruleTestCaseMapper.selectList(wrapper));
    }

    /**
     * 保存测试用例
     *
     * @param testCase 测试用例
     * @return 保存后的测试用例
     */
    @PostMapping("/test-cases")
    public Result<RuleTestCaseDO> saveTestCase(@RequestBody RuleTestCaseDO testCase) {
        if (testCase.getId() != null) {
            ruleTestCaseMapper.updateById(testCase);
        } else {
            ruleTestCaseMapper.insert(testCase);
        }
        return Result.ok(testCase);
    }

    /**
     * 删除测试用例
     *
     * @param id 测试用例 ID
     * @return 操作结果
     */
    @OperationLog(module = "规则引擎", action = "删除测试用例", bizType = "RULE_TEST_CASE")
    @DeleteMapping("/test-cases/{id}")
    public Result<Void> deleteTestCase(@PathVariable String id) {
        ruleTestCaseMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 批量执行测试用例（回归测试）
     *
     * <p>对每个测试用例执行 dry-run，对比实际触发规则与预期触发规则，
     * 返回通过率报告。支持 CI 集成：当 anyFail=true 时 HTTP 状态码仍为 200，
     * CI 脚本通过 response body 中的 passRate 判断是否阻断流水线。
     *
     * @param request 请求体，包含 ids（测试用例 ID 列表，为空则执行全部）
     * @return 回归测试报告（含每个用例的 pass/fail + 通过率统计）
     */
    @PostMapping("/test-cases/batch-run")
    public Result<Map<String, Object>> batchRunTestCases(@Valid @RequestBody TestCaseBatchRunDTO dto) {
        List<Long> ids = dto.getIds();

        List<RuleTestCaseDO> testCases;
        if (ids == null || ids.isEmpty()) {
            // 执行全部测试用例
            testCases = ruleTestCaseMapper.selectList(null);
        } else {
            testCases = ids.stream()
                .map(ruleTestCaseMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        if (testCases.isEmpty()) {
            return Result.ok(Map.of("total", 0, "passed", 0, "failed", 0, "passRate", "100%"));
        }

        List<Map<String, Object>> caseResults = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        for (RuleTestCaseDO tc : testCases) {
            List<RuleResult> results = ruleAdminService.dryRun(null, tc.getFactsData());

            // 获取实际触发的规则编码集合
            Set<String> actualTriggered = results.stream()
                .map(RuleResult::getRuleCode)
                .collect(Collectors.toSet());

            // 获取预期触发的规则编码集合
            Set<String> expectedTriggered = new HashSet<>();
            if (tc.getExpectedTriggered() != null) {
                expectedTriggered.addAll(tc.getExpectedTriggered());
            }

            // 对比
            boolean isPass = actualTriggered.equals(expectedTriggered);
            if (isPass) {
                passed++;
            } else {
                failed++;
            }

            Set<String> missing = new LinkedHashSet<>(expectedTriggered);
            missing.removeAll(actualTriggered);

            Set<String> unexpected = new LinkedHashSet<>(actualTriggered);
            unexpected.removeAll(expectedTriggered);

            Map<String, Object> caseResult = new LinkedHashMap<>();
            caseResult.put("testCaseId", tc.getId());
            caseResult.put("testCaseName", tc.getName());
            caseResult.put("ruleCode", tc.getRuleCode());
            caseResult.put("pass", isPass);
            caseResult.put("expectedTriggered", expectedTriggered);
            caseResult.put("actualTriggered", actualTriggered);
            caseResult.put("missing", missing);
            caseResult.put("unexpected", unexpected);
            caseResult.put("results", results);
            caseResults.add(caseResult);
        }

        double passRate = (double) passed / testCases.size() * 100;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", testCases.size());
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("passRate", String.format("%.1f%%", passRate));
        report.put("allPassed", failed == 0);
        report.put("caseResults", caseResults);

        return Result.ok(report);
    }

    // ==================== 生命周期管理 ====================

    /**
     * 规则状态变更
     *
     * @param ruleCode   规则编码
     * @param request    请求体，包含 targetStatus/comment
     * @param operator   操作人
     * @return 操作结果
     */
    @PutMapping("/{ruleCode}/status")
    @PrePermission("execution:rule:status")
    public Result<RuleDefinition> changeStatus(@PathVariable String ruleCode,
                                               @Valid @RequestBody RuleStatusChangeDTO dto,
                                               @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        String targetStatus = dto.getTargetStatus();
        String comment = dto.getComment() == null ? "" : dto.getComment();
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        RuleStatus current = RuleStatus.valueOf(def.getStatus());
        RuleStatus target = RuleStatus.valueOf(targetStatus);
        if (!current.canTransitionTo(target)) {
            throw new IllegalArgumentException("不允许从 " + current.getDesc() + " 变更到 " + target.getDesc());
        }
        def.setStatus(targetStatus);
        if (target == RuleStatus.PUBLISHED) {
            def.setReviewedBy(operator);
            def.setReviewedAt(LocalDateTime.now().toString());
            def.setReviewComment(comment);
        }
        return Result.ok(ruleAdminService.save(def, operator, "状态变更: " + current.getDesc() + " -> " + target.getDesc()));
    }

    /**
     * 审批通过（1.4.0 起支持）
     *
     * <p>将规则从 DRAFT/REVIEW 状态变更为 PUBLISHED，并记录审批人、审批时间、审批意见。
     * 主要用于 AI 生成规则的闭环：AI 生成 → DRAFT → 人工审批 → PUBLISHED。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 comment（审批意见）
     * @param operator 审批人
     * @return 审批后的规则定义
     */
    @PostMapping("/{ruleCode}/approve")
    @PrePermission("execution:rule:approve")
    public Result<RuleDefinition> approve(@PathVariable String ruleCode,
                                           @Valid @RequestBody RuleApproveDTO dto,
                                           @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }

        RuleStatus current = parseStatusSafely(def.getStatus());
        if (!current.canTransitionTo(RuleStatus.PUBLISHED)) {
            return Result.fail("当前状态 " + current.getDesc() + " 不允许审批通过，仅 DRAFT/REVIEW 可审批");
        }

        String comment = dto.getComment() == null ? "" : dto.getComment();

        // 记录审批留痕
        def.setStatus(RuleStatus.PUBLISHED.name());
        def.setReviewedBy(operator);
        def.setReviewedAt(LocalDateTime.now().toString());
        def.setReviewComment(comment);
        // 审批通过后默认启用（运营可后续手动 toggle 关闭）
        def.setEnabled(true);

        String changeDesc = String.format("[审批通过] %s -> PUBLISHED, 审批人=%s, 意见=%s",
                current.getDesc(), operator, comment.isEmpty() ? "无" : comment);
        return Result.ok(ruleAdminService.save(def, operator, changeDesc));
    }

    /**
     * 审批驳回（1.4.0 起支持）
     *
     * <p>将规则从 DRAFT/REVIEW 状态变更为 ARCHIVED，并记录驳回理由。
     * 主要用于 AI 生成规则的闭环：AI 生成 → DRAFT → 人工驳回 → ARCHIVED。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 reason（驳回理由，必填）
     * @param operator 审批人
     * @return 驳回后的规则定义
     */
    @PostMapping("/{ruleCode}/reject")
    @PrePermission("execution:rule:approve")
    public Result<RuleDefinition> reject(@PathVariable String ruleCode,
                                          @Valid @RequestBody RuleRejectDTO dto,
                                          @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }

        RuleStatus current = parseStatusSafely(def.getStatus());
        if (!current.canTransitionTo(RuleStatus.ARCHIVED)) {
            return Result.fail("当前状态 " + current.getDesc() + " 不允许驳回，仅 DRAFT/REVIEW/PUBLISHED 可驳回");
        }

        String reason = dto.getReason();
        // @NotBlank 已校验非空，移除手动校验

        // 记录驳回留痕
        def.setStatus(RuleStatus.ARCHIVED.name());
        def.setReviewedBy(operator);
        def.setReviewedAt(LocalDateTime.now().toString());
        def.setReviewComment("[驳回] " + reason);
        def.setEnabled(false);

        String changeDesc = String.format("[审批驳回] %s -> ARCHIVED, 审批人=%s, 理由=%s",
                current.getDesc(), operator, reason);
        return Result.ok(ruleAdminService.save(def, operator, changeDesc));
    }

    /**
     * 安全解析规则状态，无效值回退到 PUBLISHED
     */
    private RuleStatus parseStatusSafely(String status) {
        try {
            return RuleStatus.valueOf(status);
        } catch (Exception e) {
            return RuleStatus.PUBLISHED;
        }
    }

    // ==================== 多级审批流（P1-3） ====================

    /**
     * 提交审核（P1-3 多级审批流）
     *
     * <p>将规则从 DRAFT 状态提交到指定审批流的第一级。flowCode 为空时使用默认 2 级审批流。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 flowCode（可选）
     * @param operator 操作人
     * @return 审批记录
     */
    @PostMapping("/{ruleCode}/submit-review")
    @PrePermission("execution:rule:save")
    @OperationLog(module = "规则引擎", action = "提交审核", bizType = "RULE")
    public Result<ApprovalRecord> submitReview(@PathVariable String ruleCode,
                                                @Valid @RequestBody(required = false) RuleSubmitReviewDTO dto,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("多级审批流服务未启用");
        }
        String flowCode = dto == null ? null : dto.getFlowCode();
        return Result.ok(svc.submitForReview(ruleCode, flowCode, operator));
    }

    /**
     * 级别审批通过（P1-3 多级审批流）
     *
     * <p>审批通过当前级别。根据审批类型（SINGLE/COUNTERSIGN/SEQUENCE）决定是否进入下一级。
     * 全部级别通过后规则状态变为 PUBLISHED。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 comment（审批意见）
     * @param operator 审批人
     * @return 审批记录
     */
    @PostMapping("/{ruleCode}/approve-level")
    @PrePermission("execution:rule:approve")
    @OperationLog(module = "规则引擎", action = "级别审批通过", bizType = "RULE")
    public Result<ApprovalRecord> approveLevel(@PathVariable String ruleCode,
                                                @Valid @RequestBody RuleApproveDTO dto,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("多级审批流服务未启用");
        }
        String comment = dto.getComment() == null ? "" : dto.getComment();
        return Result.ok(svc.approve(ruleCode, operator, comment));
    }

    /**
     * 级别审批驳回（P1-3 多级审批流）
     *
     * <p>驳回当前级别，回退到上一级。一级驳回回退到 DRAFT。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 reason（驳回理由，必填）
     * @param operator 审批人
     * @return 审批记录
     */
    @PostMapping("/{ruleCode}/reject-level")
    @PrePermission("execution:rule:approve")
    @OperationLog(module = "规则引擎", action = "级别审批驳回", bizType = "RULE")
    public Result<ApprovalRecord> rejectLevel(@PathVariable String ruleCode,
                                               @Valid @RequestBody RuleRejectDTO dto,
                                               @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("多级审批流服务未启用");
        }
        return Result.ok(svc.reject(ruleCode, operator, dto.getReason()));
    }

    /**
     * 委托审批（P1-3 多级审批流）
     *
     * <p>将当前级别的审批权委托给他人。委托后被委托人通过 approve-level 完成审批。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 delegatedTo（被委托人工号，必填）和 comment（委托说明）
     * @param operator 委托人
     * @return 审批记录
     */
    @PostMapping("/{ruleCode}/delegate")
    @PrePermission("execution:rule:approve")
    @OperationLog(module = "规则引擎", action = "委托审批", bizType = "RULE")
    public Result<ApprovalRecord> delegate(@PathVariable String ruleCode,
                                            @Valid @RequestBody RuleDelegateDTO dto,
                                            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("多级审批流服务未启用");
        }
        String comment = dto.getComment() == null ? "" : dto.getComment();
        return Result.ok(svc.delegate(ruleCode, operator, dto.getDelegatedTo(), comment));
    }

    /**
     * 查询审批状态（P1-3 多级审批流）
     *
     * @param ruleCode 规则编码
     * @return 审批记录；无审批记录时返回 null
     */
    @GetMapping("/{ruleCode}/approval-status")
    public Result<ApprovalRecord> approvalStatus(@PathVariable String ruleCode) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.ok(null);
        }
        return Result.ok(svc.getApprovalStatus(ruleCode));
    }

    /**
     * 查询待审批列表（P1-3 多级审批流）
     *
     * @param approver 审批人工号
     * @return 待审批记录列表
     */
    @GetMapping("/pending-approvals")
    public Result<List<ApprovalRecord>> pendingApprovals(@RequestParam String approver) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.ok(List.of());
        }
        return Result.ok(svc.listPendingApprovals(approver));
    }

    /**
     * 撤回审核（P1-3 多级审批流）
     *
     * <p>将规则从审核中状态撤回到 DRAFT。仅 PENDING/DELEGATED 状态可撤回。
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     * @return 审批记录
     */
    @PostMapping("/{ruleCode}/cancel-review")
    @PrePermission("execution:rule:save")
    @OperationLog(module = "规则引擎", action = "撤回审核", bizType = "RULE")
    public Result<ApprovalRecord> cancelReview(@PathVariable String ruleCode,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("多级审批流服务未启用");
        }
        return Result.ok(svc.cancelReview(ruleCode, operator));
    }

    /**
     * 查询可用审批流配置（P1-3 多级审批流）
     *
     * @return 审批流配置列表
     */
    @GetMapping("/approval-flows")
    public Result<List<ApprovalFlow>> approvalFlows() {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.ok(List.of());
        }
        return Result.ok(svc.listFlows());
    }

    // ==================== 执行链路追踪 ====================

    /**
     * 按 traceId 查询执行链路
     */
    @GetMapping("/traces/{traceId}")
    public Result<List<RuleExecutionTraceDO>> getTrace(@PathVariable String traceId) {
        return Result.ok(ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .eq(RuleExecutionTraceDO::getTraceId, traceId)
                .orderByAsc(RuleExecutionTraceDO::getCreatedAt)));
    }

    /**
     * 按规则编码查询最近链路
     */
    @GetMapping("/traces/rule/{ruleCode}")
    public Result<List<RuleExecutionTraceDO>> getTracesByRule(@PathVariable String ruleCode,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Result.ok(ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .eq(RuleExecutionTraceDO::getRuleCode, ruleCode)
                .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                .last("LIMIT " + limit)));
    }

    /**
     * 执行回放：基于 traceId 重放历史执行链路
     *
     * <p>从历史 trace 记录中读取 factsSnapshot，用当前规则集重新评估，
     * 对比历史结果与当前结果，展示规则变更后的差异。
     *
     * @param traceId 追踪 ID
     * @return 回放结果（含历史快照 + 当前评估 + 差异分析）
     */
    @PostMapping("/traces/{traceId}/replay")
    public Result<Map<String, Object>> replayTrace(@PathVariable String traceId) {
        List<RuleExecutionTraceDO> traces = ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .eq(RuleExecutionTraceDO::getTraceId, traceId)
                .orderByAsc(RuleExecutionTraceDO::getCreatedAt));

        if (traces.isEmpty()) {
            return Result.fail("未找到 traceId=" + traceId + " 的执行记录");
        }

        // 取第一条 trace 的 factsSnapshot 作为回放输入
        Map<String, Object> facts = traces.get(0).getFactsSnapshot();
        if (facts == null || facts.isEmpty()) {
            return Result.fail("traceId=" + traceId + " 的事实快照为空，无法回放");
        }

        // 用当前规则集重新评估
        List<RuleResult> currentResults = ruleAdminService.dryRun(null, facts);

        // 构建历史触发规则编码集合
        Set<String> historicalTriggered = traces.stream()
            .filter(t -> Boolean.TRUE.equals(t.getTriggered()))
            .map(RuleExecutionTraceDO::getRuleCode)
            .collect(Collectors.toSet());

        // 构建当前触发规则编码集合
        Set<String> currentTriggered = currentResults.stream()
            .map(RuleResult::getRuleCode)
            .collect(Collectors.toSet());

        // 差异分析
        Set<String> added = new LinkedHashSet<>(currentTriggered);
        added.removeAll(historicalTriggered);

        Set<String> removed = new LinkedHashSet<>(historicalTriggered);
        removed.removeAll(currentTriggered);

        Set<String> unchanged = new LinkedHashSet<>(currentTriggered);
        unchanged.retainAll(historicalTriggered);

        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("traceId", traceId);
        replay.put("factsSnapshot", facts);
        replay.put("historicalTraces", traces);
        replay.put("currentResults", currentResults);
        replay.put("diff", Map.of(
            "added", added,
            "removed", removed,
            "unchanged", unchanged,
            "summary", String.format("新增触发 %d 条，移除触发 %d 条，保持不变 %d 条",
                added.size(), removed.size(), unchanged.size())
        ));

        return Result.ok(replay);
    }

    /**
     * P2-1 批量历史数据回放
     *
     * <p>按时间范围查询历史 trace，用当前规则集重新评估每条 trace 的事实快照，
     * 对比历史结果与当前结果，生成差异报告。
     *
     * <p>差异类型：
     * <ul>
     *   <li>consistent：历史与当前触发状态一致</li>
     *   <li>diff：历史与当前触发状态不一致（含触发→未触发、未触发→触发、严重度变化）</li>
     * </ul>
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "startTime": "2026-07-01T00:00:00",
     *   "endTime": "2026-07-07T00:00:00",
     *   "ruleCode": "EVM_RED_ALERT",  // 可选，为空表示全部规则
     *   "limit": 100                   // 默认 100，最大 1000
     * }
     * </pre>
     *
     * @param request 请求体（startTime / endTime / ruleCode / limit）
     * @return 批量回放差异报告
     */
    @PostMapping("/traces/batch-replay")
    public Result<Map<String, Object>> batchReplayTraces(@RequestBody Map<String, Object> request) {
        // 解析请求参数
        String startTimeStr = (String) request.get("startTime");
        String endTimeStr = (String) request.get("endTime");
        String ruleCode = (String) request.get("ruleCode");
        int limit = request.containsKey("limit")
                ? ((Number) request.get("limit")).intValue() : 100;
        if (limit <= 0 || limit > 1000) {
            limit = 100;
        }

        if (startTimeStr == null || endTimeStr == null) {
            return Result.fail("startTime 和 endTime 不能为空");
        }

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

        // 按时间范围查询历史 trace（可选按 ruleCode 过滤）
        LambdaQueryWrapper<RuleExecutionTraceDO> wrapper = new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .ge(RuleExecutionTraceDO::getCreatedAt, startTime)
                .lt(RuleExecutionTraceDO::getCreatedAt, endTime)
                .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                .last("LIMIT " + limit);
        if (ruleCode != null && !ruleCode.isBlank()) {
            wrapper.eq(RuleExecutionTraceDO::getRuleCode, ruleCode);
        }
        List<RuleExecutionTraceDO> traces = ruleExecutionTraceMapper.selectList(wrapper);

        // 逐条回放：用当前规则集重新评估
        List<Map<String, Object>> diffs = new ArrayList<>();
        int consistentCount = 0;
        int diffCount = 0;

        for (RuleExecutionTraceDO trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot();
            if (facts == null || facts.isEmpty()) {
                continue;
            }

            // 用当前规则集对单条规则重新评估
            List<RuleResult> currentResults = ruleAdminService.dryRun(trace.getRuleCode(), facts);
            RuleResult currentResult = currentResults.stream()
                    .filter(r -> trace.getRuleCode().equals(r.getRuleCode()))
                    .findFirst()
                    .orElse(null);

            boolean historicalTriggered = Boolean.TRUE.equals(trace.getTriggered());
            boolean currentTriggered = currentResult != null && currentResult.isTriggered();
            String historicalSeverity = trace.getSeverity();
            String currentSeverity = currentResult != null && currentResult.getSeverity() != null
                    ? currentResult.getSeverity().name() : null;

            // 严重度归一化（null 视为一致）
            boolean severityConsistent = severityEquals(historicalSeverity, currentSeverity);

            if (historicalTriggered == currentTriggered && severityConsistent) {
                consistentCount++;
            } else {
                diffCount++;
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("traceId", trace.getTraceId());
                diff.put("ruleCode", trace.getRuleCode());
                diff.put("historicalTriggered", historicalTriggered);
                diff.put("currentTriggered", currentTriggered);
                diff.put("historicalSeverity", historicalSeverity);
                diff.put("currentSeverity", currentSeverity);
                diff.put("diffType", classifyDiff(historicalTriggered, currentTriggered,
                        severityConsistent));
                diffs.add(diff);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalReplayed", traces.size());
        report.put("consistentCount", consistentCount);
        report.put("diffCount", diffCount);
        report.put("diffs", diffs);
        report.put("summary", String.format("共回放 %d 条，一致 %d 条，差异 %d 条",
                traces.size(), consistentCount, diffCount));

        return Result.ok(report);
    }

    /**
     * P2-2 规则变更影响分析
     *
     * <p>接收规则定义变更（新条件表达式），从历史 trace 中查询该规则最近 N 条记录，
     * 用新表达式重新评估每条 trace 的事实快照，预览变更后的影响范围。
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "conditionExpression": "evmRedCount >= 5",
     *   "severityExpression": "evmRedCount >= 10 ? 'RED' : 'YELLOW'",
     *   "defaultSeverity": "YELLOW",
     *   "limit": 1000
     * }
     * </pre>
     *
     * <p>影响类型：
     * <ul>
     *   <li>added：历史未触发，新表达式触发（新增触发）</li>
     *   <li>removed：历史触发，新表达式未触发（减少触发）</li>
     *   <li>severityChanged：触发状态不变，但严重度变化</li>
     *   <li>unchanged：触发状态和严重度均不变</li>
     * </ul>
     *
     * @param ruleCode 规则编码
     * @param request  请求体（conditionExpression / severityExpression / defaultSeverity / limit）
     * @return 影响分析报告
     */
    @PostMapping("/{ruleCode}/impact-preview")
    public Result<Map<String, Object>> impactPreview(@PathVariable String ruleCode,
                                                      @RequestBody Map<String, Object> request) {
        String conditionExpression = (String) request.get("conditionExpression");
        String severityExpression = (String) request.get("severityExpression");
        String defaultSeverityStr = (String) request.get("defaultSeverity");
        int limit = request.containsKey("limit")
                ? ((Number) request.get("limit")).intValue() : 1000;
        if (limit <= 0 || limit > 5000) {
            limit = 1000;
        }

        if (conditionExpression == null || conditionExpression.isBlank()) {
            return Result.fail("conditionExpression 不能为空");
        }

        // 解析默认严重度
        com.njydsz.pmis.literule.api.RuleSeverity defaultSeverity = null;
        if (defaultSeverityStr != null && !defaultSeverityStr.isBlank()) {
            try {
                defaultSeverity = com.njydsz.pmis.literule.api.RuleSeverity.valueOf(defaultSeverityStr);
            } catch (IllegalArgumentException e) {
                return Result.fail("非法的 defaultSeverity: " + defaultSeverityStr
                        + "，合法值: INFO / YELLOW / RED");
            }
        }

        // 查询该规则最近 N 条 trace
        List<RuleExecutionTraceDO> traces = ruleExecutionTraceMapper.selectList(
                new LambdaQueryWrapper<RuleExecutionTraceDO>()
                        .eq(RuleExecutionTraceDO::getRuleCode, ruleCode)
                        .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                        .last("LIMIT " + limit));

        // 逐条用新表达式重新评估
        List<Map<String, Object>> affectedTraces = new ArrayList<>();
        int historicalTriggeredCount = 0;
        int newTriggeredCount = 0;
        int addedTriggeredCount = 0;
        int removedTriggeredCount = 0;

        for (RuleExecutionTraceDO trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot();
            if (facts == null || facts.isEmpty()) {
                continue;
            }

            // 用新表达式评估
            RuleResult newResult = ruleAdminService.evaluateWithExpression(
                    ruleCode, conditionExpression, severityExpression, defaultSeverity, facts);

            boolean historicalTriggered = Boolean.TRUE.equals(trace.getTriggered());
            boolean newTriggered = newResult.isTriggered();
            String historicalSeverity = trace.getSeverity();
            String newSeverity = newResult.getSeverity() != null
                    ? newResult.getSeverity().name() : null;

            if (historicalTriggered) {
                historicalTriggeredCount++;
            }
            if (newTriggered) {
                newTriggeredCount++;
            }

            // 分类影响
            String impactType;
            if (!historicalTriggered && newTriggered) {
                addedTriggeredCount++;
                impactType = "added";
            } else if (historicalTriggered && !newTriggered) {
                removedTriggeredCount++;
                impactType = "removed";
            } else if (historicalTriggered == newTriggered && !severityEquals(historicalSeverity, newSeverity)) {
                impactType = "severityChanged";
            } else {
                impactType = "unchanged";
            }

            // 仅记录受影响的 trace（非 unchanged）
            if (!"unchanged".equals(impactType)) {
                Map<String, Object> affected = new LinkedHashMap<>();
                affected.put("traceId", trace.getTraceId());
                affected.put("historicalTriggered", historicalTriggered);
                affected.put("newTriggered", newTriggered);
                affected.put("historicalSeverity", historicalSeverity);
                affected.put("newSeverity", newSeverity);
                affected.put("impactType", impactType);
                affected.put("createdAt", trace.getCreatedAt());
                affectedTraces.add(affected);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ruleCode", ruleCode);
        report.put("conditionExpression", conditionExpression);
        report.put("totalTraces", traces.size());
        report.put("historicalTriggeredCount", historicalTriggeredCount);
        report.put("newTriggeredCount", newTriggeredCount);
        report.put("addedTriggeredCount", addedTriggeredCount);
        report.put("removedTriggeredCount", removedTriggeredCount);
        report.put("affectedTraces", affectedTraces);
        report.put("summary", String.format(
                "共分析 %d 条 trace，历史触发 %d 条，新表达式触发 %d 条（新增 %d，减少 %d）",
                traces.size(), historicalTriggeredCount, newTriggeredCount,
                addedTriggeredCount, removedTriggeredCount));

        return Result.ok(report);
    }

    /**
     * 比较两个严重度字符串是否一致（null 与 null 视为一致）
     *
     * @param s1 严重度 1
     * @param s2 严重度 2
     * @return true=一致
     */
    private boolean severityEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equalsIgnoreCase(s2);
    }

    /**
     * 分类差异类型
     *
     * @param historicalTriggered 历史是否触发
     * @param currentTriggered    当前是否触发
     * @param severityConsistent  严重度是否一致
     * @return 差异类型：triggered_to_not / not_to_triggered / severity_changed / consistent
     */
    private String classifyDiff(boolean historicalTriggered, boolean currentTriggered,
                                 boolean severityConsistent) {
        if (historicalTriggered && !currentTriggered) return "triggered_to_not";
        if (!historicalTriggered && currentTriggered) return "not_to_triggered";
        if (!severityConsistent) return "severity_changed";
        return "consistent";
    }

    /**
     * 查询最近执行链路（按时间倒序）
     *
     * @param limit 返回条数（默认 50）
     * @return 最近的执行链路列表
     */
    @GetMapping("/traces")
    public Result<List<RuleExecutionTraceDO>> listRecentTraces(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                .last("LIMIT " + limit)));
    }

    // ==================== 决策表管理 ====================

    /**
     * 查询全部决策表
     */
    @GetMapping("/decision-tables")
    public Result<List<DecisionTableDO>> listDecisionTables() {
        return Result.ok(decisionTableMapper.selectList(null));
    }

    /**
     * 查询单条决策表
     */
    @GetMapping("/decision-tables/{tableCode}")
    public Result<DecisionTableDO> getDecisionTable(@PathVariable String tableCode) {
        DecisionTableDO dt = decisionTableMapper.selectOne(
            new LambdaQueryWrapper<DecisionTableDO>().eq(DecisionTableDO::getTableCode, tableCode));
        return Result.ok(dt);
    }

    /**
     * 保存决策表
     */
    @PostMapping("/decision-tables")
    public Result<DecisionTableDO> saveDecisionTable(@RequestBody DecisionTableDO decisionTable) {
        if (decisionTable.getId() != null) {
            decisionTableMapper.updateById(decisionTable);
        } else {
            decisionTableMapper.insert(decisionTable);
        }
        return Result.ok(decisionTable);
    }

    /**
     * 删除决策表
     */
    @OperationLog(module = "规则引擎", action = "删除决策表", bizType = "DECISION_TABLE")
    @DeleteMapping("/decision-tables/{id}")
    public Result<Void> deleteDecisionTable(@PathVariable String id) {
        decisionTableMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 评估决策表
     *
     * <p>按 tableCode 加载已启用的决策表，以请求体中的 facts 作为事实数据执行 DMN 评估，
     * 返回命中行的动作值列表（无命中时返回默认动作或空列表）。
     *
     * @param tableCode 决策表编码
     * @param facts     事实数据（变量名 -> 值）
     * @return 命中行的动作值列表
     */
    @PostMapping("/decision-tables/{tableCode}/evaluate")
    public Result<List<Map<String, Object>>> evaluateDecisionTable(@PathVariable String tableCode,
                                                                   @RequestBody Map<String, Object> facts) {
        try {
            return Result.ok(decisionTableEvalService.evaluate(tableCode, facts));
        } catch (Exception e) {
            log.warn("[DecisionTable] 评估失败: tableCode={}, err={}", tableCode, e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 导出决策表为 Excel（P0-3）
     *
     * <p>将指定决策表导出为 .xlsx 文件，便于业务人员离线编辑或备份。
     *
     * @param tableCode 决策表编码
     * @return xlsx 文件流（Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet）
     */
    @OperationLog(module = "规则引擎", action = "导出决策表 Excel", bizType = "DECISION_TABLE")
    @GetMapping("/decision-tables/{tableCode}/export-excel")
    @PrePermission("execution:rule:view")
    public ResponseEntity<byte[]> exportDecisionTableExcel(@PathVariable String tableCode) {
        DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
        if (svc == null) {
            return ResponseEntity.internalServerError().build();
        }
        byte[] bytes = svc.exportExcel(tableCode);
        String fileName = URLEncoder.encode(tableCode + ".xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 导入决策表 Excel（P0-3）
     *
     * <p>上传 .xlsx 文件，解析为决策表定义并保存。支持新增和更新（按 tableCode 覆盖）。
     *
     * @param file     xlsx 文件（multipart/form-data）
     * @param operator 操作人
     * @return 保存后的决策表定义
     */
    @OperationLog(module = "规则引擎", action = "导入决策表 Excel", bizType = "DECISION_TABLE")
    @PostMapping(value = "/decision-tables/import-excel", consumes = "multipart/form-data")
    @PrePermission("execution:rule:save")
    public Result<com.njydsz.pmis.literule.api.DecisionTableDefinition> importDecisionTableExcel(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("决策表管理服务未启用");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("上传文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            com.njydsz.pmis.literule.api.DecisionTableDefinition saved = svc.importExcel(bytes, operator);
            return Result.ok(saved);
        } catch (IllegalArgumentException e) {
            log.warn("[DecisionTable] Excel 导入失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (IOException e) {
            log.warn("[DecisionTable] Excel 文件读取失败: {}", e.getMessage());
            return Result.fail("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 下载决策表 Excel 空白模板（P0-3）
     *
     * <p>返回预填充列结构的 .xlsx 模板，用户填写后通过 /import-excel 上传。
     *
     * @return xlsx 模板文件流
     */
    @GetMapping("/decision-tables/excel-template")
    @PrePermission("execution:rule:view")
    public ResponseEntity<byte[]> downloadDecisionTableExcelTemplate() {
        DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
        if (svc == null) {
            return ResponseEntity.internalServerError().build();
        }
        byte[] bytes = svc.exportExcelTemplate();
        String fileName = URLEncoder.encode("decision-table-template.xlsx", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ==================== 规则导入导出 ====================

    /**
     * 导出全部规则为 JSON
     */
    @GetMapping("/export")
    public Result<Map<String, Object>> exportRules() {
        List<RuleDefinition> rules = ruleAdminService.listAll();
        // 过滤掉内部字段，只导出核心配置
        List<Map<String, Object>> exportData = rules.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", r.getCode());
            map.put("name", r.getName());
            map.put("category", r.getCategory());
            map.put("description", r.getDescription());
            map.put("conditionExpression", r.getConditionExpression());
            map.put("severityExpression", r.getSeverityExpression());
            map.put("defaultSeverity", r.getDefaultSeverity() != null ? r.getDefaultSeverity().name() : null);
            map.put("titleTemplate", r.getTitleTemplate());
            map.put("descriptionTemplate", r.getDescriptionTemplate());
            map.put("priority", r.getPriority());
            map.put("scope", r.getScope());
            map.put("drilldownAvailable", r.isDrilldownAvailable());
            map.put("status", r.getStatus());
            map.put("version", r.getVersion());
            return map;
        }).collect(java.util.stream.Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportTime", LocalDateTime.now().toString());
        result.put("ruleCount", rules.size());
        result.put("rules", exportData);
        return Result.ok(result);
    }

    /**
     * 导出全部规则为 YAML（P2-11 GitOps）
     *
     * <p>供 GitOps 工作流使用：CI 定时拉取 YAML → 提交到 Git 仓库 →
     * 审核合并后通过 Webhook 触发 /import 同步回 DB，实现规则即代码。
     *
     * @return YAML 文本（Content-Type: text/plain）
     */
    @GetMapping(value = "/export.yaml", produces = "text/plain;charset=UTF-8")
    public String exportRulesAsYaml() {
        List<RuleDefinition> rules = ruleAdminService.listAll();
        StringBuilder sb = new StringBuilder();
        sb.append("# LiteRule 规则导出（YAML）\n");
        sb.append("# 导出时间: ").append(LocalDateTime.now()).append("\n");
        sb.append("# 规则数量: ").append(rules.size()).append("\n");
        sb.append("# 用途: GitOps 规则即代码，提交到 Git 仓库后通过 CI 校验与 Webhook 发布\n\n");
        sb.append("rules:\n");
        for (RuleDefinition r : rules) {
            sb.append("  - code: ").append(r.getCode()).append("\n");
            sb.append("    name: ").append(escapeYaml(r.getName())).append("\n");
            sb.append("    category: ").append(r.getCategory()).append("\n");
            if (r.getDescription() != null) {
                sb.append("    description: ").append(escapeYaml(r.getDescription())).append("\n");
            }
            sb.append("    conditionExpression: ").append(escapeYaml(r.getConditionExpression())).append("\n");
            if (r.getSeverityExpression() != null) {
                sb.append("    severityExpression: ").append(escapeYaml(r.getSeverityExpression())).append("\n");
            }
            sb.append("    defaultSeverity: ")
                    .append(r.getDefaultSeverity() != null ? r.getDefaultSeverity().name() : "YELLOW").append("\n");
            if (r.getTitleTemplate() != null) {
                sb.append("    titleTemplate: ").append(escapeYaml(r.getTitleTemplate())).append("\n");
            }
            if (r.getDescriptionTemplate() != null) {
                sb.append("    descriptionTemplate: ").append(escapeYaml(r.getDescriptionTemplate())).append("\n");
            }
            sb.append("    priority: ").append(r.getPriority()).append("\n");
            if (r.getScope() != null) {
                sb.append("    scope: ").append(r.getScope()).append("\n");
            }
            sb.append("    version: ").append(r.getVersion()).append("\n");
            if (r.getTenantId() != null && !"1".equals(r.getTenantId())) {
                sb.append("    tenantId: ").append(r.getTenantId()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * YAML 字符串转义（处理特殊字符与换行）
     */
    private String escapeYaml(String s) {
        if (s == null) return "null";
        // 含特殊字符时用双引号包裹并转义
        if (s.contains(":") || s.contains("#") || s.contains("\n") || s.contains("\"")) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
        return s;
    }

    /**
     * 导入规则（JSON 格式）
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importRules(@Valid @RequestBody RuleImportDTO dto,
                                                    @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<Map<String, Object>> rules = dto.getRules();
        if (rules == null || rules.isEmpty()) {
            return Result.ok(Map.of("imported", 0, "skipped", 0));
        }
        int imported = 0;
        int skipped = 0;
        for (Map<String, Object> ruleMap : rules) {
            try {
                String code = (String) ruleMap.get("code");
                if (code == null || code.isBlank()) {
                    skipped++;
                    continue;
                }
                RuleDefinition def = objectMapper.convertValue(ruleMap, RuleDefinition.class);
                // 导入时重置版本和状态
                def.setVersion(1);
                def.setStatus("DRAFT");
                ruleAdminService.save(def, operator, "导入规则");
                imported++;
            } catch (Exception e) {
                log.warn("[LiteRule] 导入规则失败: {}", e.getMessage());
                skipped++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        return Result.ok(result);
    }

    // ==================== 规则删除（P0-4） ====================

    /**
     * 删除规则（软删除：将状态置为 ARCHIVED，保留版本历史）
     *
     * <p>P0-4 关键修复：补全前端规则引擎页"删除"按钮对应的后端接口。
     * 软删除策略：status=ARCHIVED + enabled=false，保留 pmis_rule_def 原行；
     * 同步清理 pmis_rule_chain_graph 画布。
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     * @return 操作结果
     */
    @OperationLog(module = "规则引擎", action = "删除规则", bizType = "RULE")
    @DeleteMapping("/{ruleCode}")
    @PrePermission("execution:rule:delete")
    public Result<Void> deleteRule(@PathVariable String ruleCode,
                                   @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }
        def.setStatus(RuleStatus.ARCHIVED.name());
        def.setEnabled(false);
        ruleAdminService.save(def, operator, "[删除] 软删除规则 status=ARCHIVED");
        // 同步删除画布
        ruleChainGraphService.delete(ruleCode);
        log.info("[LiteRule] 规则已删除: ruleCode={}, operator={}", ruleCode, operator);
        return Result.ok();
    }

    // ==================== 批量操作（P0-5） ====================

    /**
     * 批量启停规则
     *
     * <p>P0-5 关键修复：列表加 checkbox 后批量操作接口。
     * 启用时同时校验 status=PUBLISHED，未发布的规则不能启用。
     *
     * @param request  请求体，包含 ruleCodes / enabled
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @PostMapping("/batch-toggle")
    @PrePermission("execution:rule:toggle")
    public Result<Map<String, Object>> batchToggle(@Valid @RequestBody RuleBatchToggleDTO dto,
                                                   @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        Boolean enabled = dto.getEnabled();
        // @NotEmpty + @NotNull 已校验非空，移除手动校验
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String code : ruleCodes) {
            try {
                RuleDefinition def = ruleAdminService.getByCode(code);
                if (def == null) {
                    failed.add(code + ": 不存在");
                    continue;
                }
                if (Boolean.TRUE.equals(enabled) && !"PUBLISHED".equals(def.getStatus())) {
                    failed.add(code + ": 未发布的规则不能启用");
                    continue;
                }
                def.setEnabled(enabled);
                ruleAdminService.save(def, operator, "[批量] " + (enabled ? "启用" : "停用"));
                success++;
            } catch (Exception e) {
                failed.add(code + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        return Result.ok(result);
    }

    /**
     * 批量调整规则优先级
     *
     * @param request  请求体，包含 ruleCodes / delta（可为负）
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @PostMapping("/batch-priority")
    public Result<Map<String, Object>> batchPriority(@Valid @RequestBody RuleBatchPriorityDTO dto,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        Integer delta = dto.getDelta();
        // @NotEmpty + @NotNull 已校验非空；delta==0 需保留手动校验（JSR-303 无原生非零约束）
        if (delta == 0) {
            return Result.fail("delta 不能为 0");
        }
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String code : ruleCodes) {
            try {
                RuleDefinition def = ruleAdminService.getByCode(code);
                if (def == null) {
                    failed.add(code + ": 不存在");
                    continue;
                }
                int newPriority = (def.getPriority()) + delta.intValue();
                // 钳制 0-100 范围
                newPriority = Math.max(0, Math.min(100, newPriority));
                def.setPriority(newPriority);
                ruleAdminService.save(def, operator, "[批量] 优先级调整 " + (delta > 0 ? "+" : "") + delta);
                success++;
            } catch (Exception e) {
                failed.add(code + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        return Result.ok(result);
    }

    /**
     * 批量调整规则分类
     *
     * @param request  请求体，包含 ruleCodes / category
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @PostMapping("/batch-category")
    public Result<Map<String, Object>> batchCategory(@Valid @RequestBody RuleBatchCategoryDTO dto,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        String category = dto.getCategory();
        // @NotEmpty + @NotBlank 已校验非空，移除手动校验
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String code : ruleCodes) {
            try {
                RuleDefinition def = ruleAdminService.getByCode(code);
                if (def == null) {
                    failed.add(code + ": 不存在");
                    continue;
                }
                def.setCategory(category);
                ruleAdminService.save(def, operator, "[批量] 分类调整为 " + category);
                success++;
            } catch (Exception e) {
                failed.add(code + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        return Result.ok(result);
    }

    // ==================== 规则链画布（P0-1） ====================

    /**
     * 查询规则的画布
     *
     * <p>P0-1：返回 RuleChainGraph JSON 内容（含 nodes/edges/viewport/metadata）。
     * 不存在时返回 200 + null，前端按空画布初始化。
     *
     * @param ruleCode 规则编码
     * @return 画布对象
     */
    @GetMapping("/{ruleCode}/graph")
    public Result<RuleChainGraph> getChainGraph(@PathVariable String ruleCode) {
        return Result.ok(ruleChainGraphService.getByRuleCode(ruleCode));
    }

    /**
     * 保存或更新画布
     *
     * <p>保存前先用 {@link RuleGraphValidator} 校验画布结构，存在 ERROR 级问题则拒绝保存。
     * 校验通过后自动递增画布版本号。
     *
     * @param ruleCode 规则编码
     * @param graph    画布
     * @param operator 操作人
     * @return 保存后的画布
     */
    @PostMapping("/{ruleCode}/graph")
    public Result<Map<String, Object>> saveChainGraph(@PathVariable String ruleCode,
                                                       @RequestBody RuleChainGraph graph,
                                                       @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        // 1. 结构校验
        List<RuleGraphValidator.GraphValidationIssue> issues = RuleGraphValidator.validate(graph);
        if (!RuleGraphValidator.isValid(issues)) {
            return Result.ok(Map.of(
                    "valid", false,
                    "issues", issues,
                    "message", "画布结构不合法，请先修复错误"
            ));
        }
        // 2. 保存
        RuleChainGraph saved = ruleChainGraphService.save(ruleCode, graph, operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("issues", issues);
        result.put("graph", saved);
        return Result.ok(result);
    }

    /**
     * 删除画布
     */
    @OperationLog(module = "规则引擎", action = "删除画布", bizType = "RULE_CHAIN_GRAPH")
    @DeleteMapping("/{ruleCode}/graph")
    public Result<Void> deleteChainGraph(@PathVariable String ruleCode) {
        ruleChainGraphService.delete(ruleCode);
        return Result.ok();
    }

    /**
     * 校验画布结构（不保存）
     *
     * <p>供前端"实时校验"按钮调用，返回 ERROR/WARN 两级问题。
     */
    @PostMapping("/{ruleCode}/graph/validate")
    public Result<List<RuleGraphValidator.GraphValidationIssue>> validateChainGraph(@RequestBody RuleChainGraph graph) {
        return Result.ok(RuleGraphValidator.validate(graph));
    }

    /**
     * 表达式求值预览（P2-8）
     *
     * <p>给定表达式与样例事实数据，返回求值结果，供前端表达式编辑器实时预览。
     *
     * @param expression 表达式
     * @param facts      样例事实数据
     * @return 求值结果（含 value / type / error）
     */
    @PostMapping("/expression-preview")
    public Result<com.njydsz.pmis.literule.expr.ExpressionPreviewResult> previewExpression(
            @RequestParam String expression,
            @RequestBody Map<String, Object> facts) {
        return Result.ok(expressionValidationService.previewEvaluate(expression, facts));
    }

    /**
     * 画布 Dry-run 仿真（P0-1 执行闭环）
     *
     * <p>将画布转换为可执行规则链后执行 Dry-run 评估，返回已触发的规则结果。
     * 画布不存在时返回空列表；画布校验失败返回 400。
     *
     * @param ruleCode 规则编码（画布关联 key）
     * @param facts    事实数据
     * @return 评估结果列表
     */
    @PostMapping("/{ruleCode}/graph/dry-run")
    public Result<List<RuleResult>> dryRunGraph(@PathVariable String ruleCode,
                                                 @RequestBody Map<String, Object> facts) {
        try {
            List<RuleResult> results = graphExecutionService.dryRunGraph(ruleCode, facts);
            return Result.ok(results);
        } catch (IllegalArgumentException e) {
            log.warn("[RuleAdmin] 画布 dry-run 失败: ruleCode={}, err={}", ruleCode, e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 检查画布中失效的规则引用（P0-1 执行闭环）
     *
     * <p>返回画布中引用了但已不存在或已禁用的规则编码列表，
     * 供前端在保存画布时提示用户修复失效节点。
     *
     * @param ruleCode 规则编码
     * @return 失效规则编码列表
     */
    @GetMapping("/{ruleCode}/graph/invalid-refs")
    public Result<List<String>> invalidGraphRefs(@PathVariable String ruleCode) {
        return Result.ok(graphExecutionService.collectInvalidReferences(ruleCode));
    }

    // ==================== 函数市场（P1-7） ====================

    /**
     * 获取已注册表达式函数列表
     *
     * <p>P1-7 函数市场：前端 CodeMirror 编辑器拉取此接口，渲染自动补全 + 悬浮文档。
     * 当前默认返回 18 个内置函数（string/math/convert/datetime/logic/type 六类）。
     *
     * @param engine 引擎类型（aviator/qlexpress/all），默认 all
     * @return 函数定义列表
     */
    @GetMapping("/expression-functions")
    public Result<List<ExpressionFunctionDef>> expressionFunctions(
            @RequestParam(value = "engine", defaultValue = "all") String engine) {
        List<ExpressionFunctionDef> all = ExpressionFunctionDef.defaults();
        List<ExpressionFunctionDef> filtered = all.stream()
                .filter(f -> "all".equalsIgnoreCase(engine)
                        || engine.equalsIgnoreCase(f.getSupportedEngines()))
                .toList();
        return Result.ok(filtered);
    }

    // ==================== 规则依赖（P1-8） ====================

    /**
     * 添加规则依赖
     */
    @PostMapping("/{ruleCode}/dependencies")
    public Result<RuleDependencyDO> addDependency(
            @PathVariable String ruleCode,
            @Valid @RequestBody RuleDependencyAddDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        String dependsOn = dto.getDependsOnRuleCode();
        String depType = dto.getDependencyType() == null ? "EXECUTE" : dto.getDependencyType();
        Boolean cascade = dto.getCascadeOnDisable() == null ? false : dto.getCascadeOnDisable();
        String description = dto.getDescription();
        return Result.ok(ruleDependencyService.add(ruleCode, dependsOn, depType, cascade, description, operator));
    }

    /**
     * 删除规则依赖
     */
    @OperationLog(module = "规则引擎", action = "删除规则依赖", bizType = "RULE_DEPENDENCY")
    @DeleteMapping("/{ruleCode}/dependencies/{dependsOnRuleCode}")
    public Result<Void> removeDependency(
            @PathVariable String ruleCode,
            @PathVariable String dependsOnRuleCode) {
        ruleDependencyService.remove(ruleCode, dependsOnRuleCode);
        return Result.ok();
    }

    /**
     * 查询规则的依赖（正向：依赖了哪些）
     */
    @GetMapping("/{ruleCode}/dependencies")
    public Result<List<RuleDependencyDO>> listDependencies(@PathVariable String ruleCode) {
        return Result.ok(ruleDependencyService.listDependencies(ruleCode));
    }

    /**
     * 查询被依赖（反向：被哪些规则依赖）
     */
    @GetMapping("/{ruleCode}/dependents")
    public Result<List<RuleDependencyDO>> listDependents(@PathVariable String ruleCode) {
        return Result.ok(ruleDependencyService.listDependents(ruleCode));
    }

    /**
     * 查询级联禁用影响（disable ruleCode 时，需要级联禁用的规则列表）
     */
    @GetMapping("/{ruleCode}/cascading-disable")
    public Result<List<String>> cascadingDisable(@PathVariable String ruleCode) {
        return Result.ok(ruleDependencyService.cascadingDisable(ruleCode));
    }

    // ==================== 规则目录树 + 责任人（P1-9） ====================

    /**
     * 获取规则目录树
     *
     * <p>树根为虚拟 ROOT，children 为一级分类。叶子节点或中间节点都包含该路径下的规则数与 Owner 列表。
     */
    @GetMapping("/category-tree")
    public Result<RuleCategoryTreeService.CategoryNode> categoryTree() {
        return Result.ok(ruleCategoryTreeService.buildTree());
    }

    /**
     * 按分类路径前缀查询规则
     *
     * @param path 分类路径前缀，例如 "finance" / "finance/credit"
     */
    @GetMapping("/by-category-path")
    public Result<List<RuleDefinition>> listByCategoryPath(
            @RequestParam(value = "path", required = false) String path) {
        return Result.ok(ruleCategoryTreeService.listDefinitionsByCategoryPath(path));
    }

    /**
     * 按 Owner 查询规则
     */
    @GetMapping("/by-owner")
    public Result<List<RuleDefinition>> listByOwner(
            @RequestParam(value = "owner") String owner) {
        return Result.ok(ruleCategoryTreeService.listDefinitionsByOwner(owner));
    }

    /**
     * 设置规则责任人
     */
    @PutMapping("/{ruleCode}/owner")
    public Result<Void> setOwner(
            @PathVariable String ruleCode,
            @RequestParam(value = "owner") String owner,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.updateOwner(ruleCode, owner, operator);
        return Result.ok();
    }

    /**
     * 设置规则分类路径
     */
    @PutMapping("/{ruleCode}/category-path")
    public Result<Void> setCategoryPath(
            @PathVariable String ruleCode,
            @RequestParam(value = "path") String path,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.updateCategoryPath(ruleCode, path, operator);
        return Result.ok();
    }

    // ==================== AB Test 自动回滚策略（P1-10） ====================

    /**
     * 获取规则的 AB Test 自动回滚策略（无配置时返回默认策略）
     */
    @GetMapping("/{ruleCode}/ab-policy")
    public Result<RuleABPolicyDO> getABPolicy(@PathVariable String ruleCode) {
        RuleABPolicyDO policy = abTestAutoRollbackService.getPolicy(ruleCode);
        return Result.ok(policy);
    }

    /**
     * 更新规则的 AB Test 自动回滚策略
     */
    @PutMapping("/{ruleCode}/ab-policy")
    public Result<Void> updateABPolicy(
            @PathVariable String ruleCode,
            @RequestBody RuleABPolicyDO policy,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        policy.setRuleCode(ruleCode);
        abTestAutoRollbackService.savePolicy(policy, operator);
        return Result.ok();
    }

    /**
     * 查询规则的回滚历史
     */
    @GetMapping("/{ruleCode}/ab-rollbacks")
    public Result<List<RuleABRollbackDO>> listRollbackHistory(@PathVariable String ruleCode) {
        return Result.ok(abTestAutoRollbackService.listRollbackHistory(ruleCode));
    }

    /**
     * 主动触发 AB Test 评估（人工立即检查）
     */
    @PostMapping("/{ruleCode}/ab-evaluate")
    public Result<Boolean> evaluateAB(@PathVariable String ruleCode) {
        return Result.ok(abTestAutoRollbackService.evaluateOne(ruleCode));
    }

    /**
     * 人工回滚（Owner 主动请求 / 紧急操作）
     *
     * @param reason MANUAL / OWNER_REQUEST
     */
    @PostMapping("/{ruleCode}/ab-rollback")
    public Result<RuleABRollbackDO> manualRollback(
            @PathVariable String ruleCode,
            @RequestParam(value = "reason", defaultValue = "MANUAL") String reason,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return Result.ok(abTestAutoRollbackService.manualRollback(ruleCode, operator, reason));
    }

    // ==================== 规则集市场（P2-14） ====================

    /**
     * 列出全部规则集（市场首页）
     */
    @GetMapping("/packs")
    public Result<List<RulePack>> listPacks() {
        return Result.ok(rulePackService.listAll());
    }

    /**
     * 搜索规则集
     */
    @GetMapping("/packs/search")
    public Result<List<RulePack>> searchPacks(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(rulePackService.search(keyword));
    }

    /**
     * 查询规则集最新版本
     */
    @GetMapping("/packs/{packCode}/latest")
    public Result<RulePack> getLatestPack(@PathVariable String packCode) {
        return Result.ok(rulePackService.getLatest(packCode));
    }

    /**
     * 查询规则集的所有版本
     */
    @GetMapping("/packs/{packCode}/versions")
    public Result<List<RulePack>> listPackVersions(@PathVariable String packCode) {
        return Result.ok(rulePackService.listVersions(packCode));
    }

    /**
     * 查询规则集指定版本（含规则定义快照，P2-8）
     */
    @GetMapping("/packs/{packCode}/versions/{version}")
    public Result<RulePack> getPackVersion(
            @PathVariable String packCode,
            @PathVariable String version) {
        return Result.ok(rulePackService.getVersion(packCode, version));
    }

    /**
     * 知识包版本回滚（P2-8）：将该版本固化的规则定义整体恢复到在线规则表
     */
    @PostMapping("/packs/{packCode}/rollback")
    @OperationLog(module = "规则引擎", action = "知识包回滚", bizType = "RULE_PACK")
    public Result<RulePackService.InstallResult> rollbackPack(
            @PathVariable String packCode,
            @RequestParam(value = "version") String version,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return Result.ok(rulePackService.rollback(packCode, version, operator));
    }

    /**
     * 知识包版本差异对比（P2-8）：对比两个版本规则编码与内容差异
     */
    @GetMapping("/packs/{packCode}/diff")
    public Result<RulePackService.PackDiff> diffPack(
            @PathVariable String packCode,
            @RequestParam(value = "from") String fromVersion,
            @RequestParam(value = "to") String toVersion) {
        return Result.ok(rulePackService.diff(packCode, fromVersion, toVersion));
    }

    /**
     * 发布规则集到市场
     */
    @PostMapping("/packs")
    public Result<RulePack> publishPack(
            @RequestBody RulePack pack,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return Result.ok(rulePackService.publish(pack, operator));
    }

    /**
     * 安装规则集（一键导入）
     */
    @PostMapping("/packs/{packCode}/install")
    public Result<RulePackService.InstallResult> installPack(
            @PathVariable String packCode,
            @RequestParam(value = "version", required = false) String version,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return Result.ok(rulePackService.install(packCode, version, operator));
    }

    /**
     * 删除规则集
     */
    @OperationLog(module = "规则引擎", action = "删除规则集", bizType = "RULE_PACK")
    @DeleteMapping("/packs/{id}")
    public Result<Void> deletePack(@PathVariable String id) {
        rulePackService.delete(id);
        return Result.ok();
    }

    /**
     * 标记为官方
     */
    @PutMapping("/packs/{id}/official")
    public Result<Void> markOfficialPack(
            @PathVariable String id,
            @RequestParam(value = "official", defaultValue = "true") boolean official) {
        rulePackService.markOfficial(id, official);
        return Result.ok();
    }

    /**
     * 评分（0-5）
     */
    @PutMapping("/packs/{id}/rate")
    public Result<Void> ratePack(
            @PathVariable String id,
            @RequestParam(value = "rating") double rating) {
        rulePackService.rate(id, rating);
        return Result.ok();
    }

    // ==================================================================
    // P2-15 AI 增强
    // ==================================================================

    /**
     * 自然语言转规则定义
     *
     * <p>调用 LLM 将自然语言描述转为结构化规则定义（含表达式、严重度、描述）。
     * LLM 不可用时降级返回空壳定义。
     *
     * @param body 请求体，含 naturalLanguage 字段
     * @return LLM 生成的规则定义
     */
    @PostMapping("/ai/nl2rule")
    @Operation(summary = "AI 自然语言转规则（NL2Rule）", description = "调用 LLM 将自然语言描述转为结构化规则定义（含表达式、严重度、描述）；LLM 不可用时降级返回空壳定义")
    public Result<RuleDefinition> naturalLanguageToRule(@Valid @RequestBody RuleNL2RuleDTO dto) {
        RuleLLMService svc = ruleLLMServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("AI 增强未启用（pmis.literule.ai.enabled=false）");
        }
        String text = dto.getNaturalLanguage();
        return Result.ok(svc.naturalLanguageToRule(text));
    }

    /**
     * 生成规则业务描述
     *
     * @param ruleCode 规则编码
     * @return 1~3 句中文描述；LLM 不可用时返回 null
     */
    @GetMapping("/{ruleCode}/ai/describe")
    @Operation(summary = "AI 生成规则描述", description = "基于规则定义生成 1~3 句中文业务描述；LLM 不可用时返回 null")
    public Result<String> describeRule(@PathVariable String ruleCode) {
        RuleLLMService svc = ruleLLMServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("AI 增强未启用");
        }
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }
        return Result.ok(svc.describeRule(def));
    }

    /**
     * 表达式优化建议
     *
     * @param ruleCode 规则编码
     * @return 优化建议文本
     */
    @GetMapping("/{ruleCode}/ai/optimize")
    @Operation(summary = "AI 表达式优化建议", description = "基于规则条件表达式生成优化建议文本")
    public Result<String> optimizeExpression(@PathVariable String ruleCode) {
        RuleLLMService svc = ruleLLMServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("AI 增强未启用");
        }
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }
        return Result.ok(svc.optimizeExpression(def.getConditionExpression()));
    }

    /**
     * 规则健康度评分
     *
     * @param ruleCode 规则编码
     * @return 健康度评分结果（0~100 + 分项 + 建议）
     */
    @GetMapping("/{ruleCode}/ai/health")
    @Operation(summary = "规则健康度评分", description = "4 维加权评分（命中率 30% + 错误率 30% + 复杂度 20% + 覆盖率 20%），返回 0~100 总分 + EXCELLENT/GOOD/WARN/BAD 等级 + 建议")
    public Result<RuleHealthScore> healthScore(@PathVariable String ruleCode) {
        RuleHealthScoreService svc = ruleHealthScoreServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("AI 增强未启用");
        }
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }
        RuleEngineStats stats = ruleEngine.getStats();
        return Result.ok(svc.score(def, stats));
    }

    /**
     * 批量规则健康度评分
     *
     * @return 全部规则的健康度评分列表
     */
    @GetMapping("/ai/health-batch")
    @Operation(summary = "批量规则健康度评分", description = "对全部规则逐条评分，返回健康度评分列表")
    public Result<List<RuleHealthScore>> healthScoreBatch() {
        RuleHealthScoreService svc = ruleHealthScoreServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("AI 增强未启用");
        }
        List<RuleDefinition> all = ruleAdminService.listAll();
        RuleEngineStats stats = ruleEngine.getStats();
        // 逐条评分：score 方法内部会从全局 stats.perRuleStats 中按规则编码取明细
        List<RuleHealthScore> result = new ArrayList<>(all.size());
        for (RuleDefinition def : all) {
            result.add(svc.score(def, stats));
        }
        return Result.ok(result);
    }

    /**
     * 规则推荐
     *
     * @param ruleCode 源规则编码
     * @return 推荐结果列表（按 score 降序）
     */
    @GetMapping("/{ruleCode}/ai/recommend")
    @Operation(summary = "规则推荐", description = "基于 4 种启发式算法（字段补全/重复检测/变体建议/拆分建议）生成推荐规则列表，按 score 降序")
    public Result<List<RuleRecommendation>> recommend(@PathVariable String ruleCode) {
        RuleRecommendationService svc = ruleRecommendationServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("AI 增强未启用");
        }
        RuleDefinition source = ruleAdminService.getByCode(ruleCode);
        if (source == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }
        List<RuleDefinition> all = ruleAdminService.listAll();
        RuleEngineStats stats = ruleEngine.getStats();
        // 将全局 stats 包装为 Map：recommend 内部按规则编码取 RuleEngineStats，
        // 再从其 perRuleStats 中按规则编码取明细
        Map<String, RuleEngineStats> statsMap = new HashMap<>();
        if (stats != null) {
            statsMap.put(source.getCode(), stats);
        }
        return Result.ok(svc.recommend(source, all, statsMap));
    }

    // ==================================================================
    // P3-3 LLM 辅助归因分析
    // ==================================================================

    /**
     * 单规则归因分析
     *
     * <p>基于 P0-2 表达式追踪能力，对指定规则用给定事实数据执行表达式追踪，
     * 生成人类可读的归因分析报告。LLM 可用时附加详细分析和建议。
     *
     * <p>请求体示例：
     * <pre>
     * POST /rules/{ruleCode}/attribution
     * {
     *   "amount": 1500,
     *   "score": 750
     * }
     * </pre>
     *
     * @param ruleCode 规则编码
     * @param facts    事实数据
     * @return 归因分析报告
     */
    @PostMapping("/{ruleCode}/attribution")
    @Operation(summary = "单规则归因分析", description = "基于表达式追踪 + LLM 生成规则触发/未触发的归因分析报告")
    public Result<AttributionReport> attribution(@PathVariable String ruleCode,
                                                   @RequestBody Map<String, Object> facts) {
        RuleAttributionService svc = ruleAttributionServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("归因分析服务未启用");
        }
        return Result.ok(svc.analyze(ruleCode, facts));
    }

    /**
     * 批量归因分析
     *
     * <p>按 traceId 列表查询历史执行轨迹，对每条轨迹的事实快照重新执行归因分析。
     *
     * @param traceIds traceId 列表
     * @return 归因分析报告列表
     */
    @PostMapping("/attribution/batch")
    @Operation(summary = "批量归因分析", description = "按 traceId 列表对历史执行轨迹批量归因分析")
    public Result<List<AttributionReport>> batchAttribution(@RequestBody List<String> traceIds) {
        RuleAttributionService svc = ruleAttributionServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("归因分析服务未启用");
        }
        if (traceIds == null || traceIds.isEmpty()) {
            return Result.ok(List.of());
        }
        List<RuleExecutionTraceDO> traces = ruleExecutionTraceMapper.selectList(
                new LambdaQueryWrapper<RuleExecutionTraceDO>()
                        .in(RuleExecutionTraceDO::getTraceId, traceIds)
                        .orderByAsc(RuleExecutionTraceDO::getCreatedAt));
        List<AttributionReport> reports = new ArrayList<>();
        for (RuleExecutionTraceDO trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot() != null
                    ? trace.getFactsSnapshot() : new HashMap<>();
            AttributionReport report = svc.analyze(trace.getRuleCode(), facts);
            report.setRuleName(trace.getRuleName());
            report.setTriggered(Boolean.TRUE.equals(trace.getTriggered()));
            report.setSeverity(trace.getSeverity());
            reports.add(report);
        }
        return Result.ok(reports);
    }

    /**
     * 基于 traceId 归因分析
     *
     * <p>按 traceId 查询执行轨迹，对每条轨迹的事实快照重新执行归因分析。
     *
     * @param traceId 追踪 ID
     * @return 归因分析报告列表
     */
    @GetMapping("/traces/{traceId}/attribution")
    @Operation(summary = "基于 traceId 归因分析", description = "按 traceId 查询执行轨迹并归因分析")
    public Result<List<AttributionReport>> traceAttribution(@PathVariable String traceId) {
        RuleAttributionService svc = ruleAttributionServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("归因分析服务未启用");
        }
        List<RuleExecutionTraceDO> traces = ruleExecutionTraceMapper.selectList(
                new LambdaQueryWrapper<RuleExecutionTraceDO>()
                        .eq(RuleExecutionTraceDO::getTraceId, traceId)
                        .orderByAsc(RuleExecutionTraceDO::getCreatedAt));
        if (traces.isEmpty()) {
            return Result.ok(List.of());
        }
        List<AttributionReport> reports = new ArrayList<>();
        for (RuleExecutionTraceDO trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot() != null
                    ? trace.getFactsSnapshot() : new HashMap<>();
            AttributionReport report = svc.analyze(trace.getRuleCode(), facts);
            report.setRuleName(trace.getRuleName());
            report.setTriggered(Boolean.TRUE.equals(trace.getTriggered()));
            report.setSeverity(trace.getSeverity());
            reports.add(report);
        }
        return Result.ok(reports);
    }

    // ==================================================================
    // P2-9 规则压测工具
    // ==================================================================

    /**
     * 规则压测
     *
     * <p>使用线程池并发执行 Dry-run，统计 QPS、P50/P95/P99 耗时、错误率等指标，
     * 用于规则变更前的性能回归验证与容量评估。
     *
     * <p>请求体示例：
     * <pre>
     * POST /rules/stress-test
     * {
     *   "ruleCode": null,
     *   "factsList": [{"budgetUsedRatio":0.95}, {"budgetUsedRatio":0.5}],
     *   "threads": 10,
     *   "iterations": 1000,
     *   "warmupIterations": 100
     * }
     * </pre>
     *
     * @param request 压测请求
     * @return 压测结果（含 QPS、分位数耗时、错误率、直方图）
     */
    @PostMapping("/stress-test")
    @Operation(summary = "规则压测", description = "使用线程池并发执行 Dry-run，统计 QPS、P50/P95/P99 耗时、错误率")
    public Result<RuleStressTestService.StressTestResult> stressTest(
            @RequestBody Map<String, Object> request) {
        RuleStressTestService svc = ruleStressTestServiceProvider.getIfAvailable();
        if (svc == null) {
            return Result.fail("规则压测服务未启用");
        }
        String ruleCode = (String) request.get("ruleCode");
        if (ruleCode != null && ruleCode.isBlank()) ruleCode = null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> factsList = (List<Map<String, Object>>) request.get("factsList");
        int threads = toInt(request.get("threads"), 10);
        int iterations = toInt(request.get("iterations"), 1000);
        int warmupIterations = toInt(request.get("warmupIterations"), 100);
        if (factsList == null || factsList.isEmpty()) {
            return Result.fail("factsList 不能为空");
        }
        return Result.ok(svc.run(ruleCode, factsList, threads, iterations, warmupIterations));
    }

    /**
     * 安全转换为 int
     */
    private int toInt(Object v, int defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================================================================
    // P2-10 知识包依赖更新提醒
    // ==================================================================

    /**
     * 检查已安装知识包的版本更新
     *
     * <p>查询当前租户已安装的知识包列表，对比每个包的已安装版本与市场最新版本，
     * 返回有更新可用的包列表。
     *
     * @return 更新检查结果列表
     */
    @GetMapping("/packs/update-check")
    @Operation(summary = "知识包更新检查", description = "对比已安装知识包与市场最新版本，返回有更新的包列表")
    public Result<List<RulePackService.PackUpdateInfo>> checkPackUpdates() {
        return Result.ok(rulePackService.checkPackUpdates());
    }

    /**
     * 批量更新知识包到最新版本
     *
     * @param operator 操作人
     * @return 每个包的更新结果
     */
    @PostMapping("/packs/batch-update")
    @OperationLog(module = "规则引擎", action = "批量更新知识包", bizType = "RULE_PACK")
    @Operation(summary = "批量更新知识包", description = "将指定知识包列表更新到最新版本")
    public Result<List<RulePackService.InstallResult>> batchUpdatePacks(
            @RequestBody List<String> packCodes,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        if (packCodes == null || packCodes.isEmpty()) {
            return Result.ok(List.of());
        }
        List<RulePackService.InstallResult> results = new ArrayList<>();
        for (String packCode : packCodes) {
            try {
                results.add(rulePackService.install(packCode, null, operator));
            } catch (Exception e) {
                log.warn("[RuleAdmin] 批量更新知识包失败: packCode={}, err={}", packCode, e.getMessage());
            }
        }
        return Result.ok(results);
    }
}
