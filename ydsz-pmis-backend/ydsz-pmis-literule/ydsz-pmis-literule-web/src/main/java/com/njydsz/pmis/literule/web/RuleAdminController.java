package com.njydsz.pmis.literule.web;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.njydsz.pmis.common.util.json.JsonUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.annotation.IdempotentExempt;
import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RulePack;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.api.dto.ExpressionValidateDTO;
import com.njydsz.pmis.literule.api.dto.RuleABTestDTO;
import com.njydsz.pmis.literule.api.dto.RuleApproveDTO;
import com.njydsz.pmis.literule.api.dto.RuleBatchCategoryDTO;
import com.njydsz.pmis.literule.api.dto.RuleBatchPriorityDTO;
import com.njydsz.pmis.literule.api.dto.RuleBatchToggleDTO;
import com.njydsz.pmis.literule.api.dto.RuleDelegateDTO;
import com.njydsz.pmis.literule.api.dto.RuleDependencyAddDTO;
import com.njydsz.pmis.literule.api.dto.RuleImportDTO;
import com.njydsz.pmis.literule.api.dto.RuleRejectDTO;
import com.njydsz.pmis.literule.api.dto.RuleStatusChangeDTO;
import com.njydsz.pmis.literule.api.dto.RuleSubmitReviewDTO;
import com.njydsz.pmis.literule.api.dto.TestCaseBatchRunDTO;
import com.njydsz.pmis.literule.domain.entity.DecisionTableDO;
import com.njydsz.pmis.literule.domain.entity.RuleABPolicyDO;
import com.njydsz.pmis.literule.domain.entity.RuleABRollbackDO;
import com.njydsz.pmis.literule.domain.entity.RuleDependencyDO;
import com.njydsz.pmis.literule.domain.entity.RuleExecutionTraceDO;
import com.njydsz.pmis.literule.domain.entity.RuleTemplateDO;
import com.njydsz.pmis.literule.domain.entity.RuleTestCaseDO;
import com.njydsz.pmis.literule.infra.mapper.DecisionTableMapper;
import com.njydsz.pmis.literule.infra.mapper.RuleExecutionTraceMapper;
import com.njydsz.pmis.literule.infra.mapper.RuleTestCaseMapper;
import com.njydsz.pmis.literule.server.approval.ApprovalFlow;
import com.njydsz.pmis.literule.server.approval.ApprovalRecord;
import com.njydsz.pmis.literule.server.approval.RuleApprovalService;
import com.njydsz.pmis.literule.server.benchmark.RuleStressTestService;
import com.njydsz.pmis.literule.server.config.ABTestService;
import com.njydsz.pmis.literule.server.config.DecisionTableAdminService;
import com.njydsz.pmis.literule.server.config.RuleAdminService;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.server.expr.ExpressionFunctionDef;
import com.njydsz.pmis.literule.server.expr.ExpressionPreviewResult;
import com.njydsz.pmis.literule.server.expr.ExpressionValidationResult;
import com.njydsz.pmis.literule.server.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.server.orchestrator.RuleChainGraph;
import com.njydsz.pmis.literule.server.orchestrator.RuleGraphValidator;
import com.njydsz.pmis.literule.server.spi.ABTestAutoRollbackProvider;
import com.njydsz.pmis.literule.server.spi.DecisionTableEvalProvider;
import com.njydsz.pmis.literule.server.spi.GraphExecutionProvider;
import com.njydsz.pmis.literule.server.spi.RuleCategoryProvider;
import com.njydsz.pmis.literule.server.spi.RuleCategoryProvider.CategoryNode;
import com.njydsz.pmis.literule.server.spi.RuleChainGraphProvider;
import com.njydsz.pmis.literule.server.spi.RuleConflictDetectorProvider;
import com.njydsz.pmis.literule.server.spi.RuleConflictDetectorProvider.RuleConflictInfo;
import com.njydsz.pmis.literule.server.spi.RuleDependencyProvider;
import com.njydsz.pmis.literule.server.spi.RulePackProvider;
import com.njydsz.pmis.literule.server.spi.RulePackProvider.InstallResult;
import com.njydsz.pmis.literule.server.spi.RulePackProvider.PackDiff;
import com.njydsz.pmis.literule.server.spi.RulePackProvider.PackUpdateInfo;
import com.njydsz.pmis.literule.server.spi.RuleTemplateProvider;
import com.njydsz.pmis.literule.server.spi.RuleVersion;
import com.njydsz.pmis.literule.server.version.RuleVersionDiff;
import com.njydsz.pmis.literule.server.version.RuleVersionDiffService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则管理 Controller
 *
 * <p>提供规则 CRUD、启停、版本管理、dry-run 仿真、执行监控等 REST API。
 *
 * <p>1.6.0 起从 project 模块迁移至 literule 模块，通过 SPI 接口反转依赖，
 * 避免 literule 直接依赖 project 模块。9 个原 project 服务依赖替换为对应 SPI：
 * <ul>
 *   <li>{@link RuleTemplateProvider} - 规则模板市场</li>
 *   <li>{@link RuleConflictDetectorProvider} - 规则冲突检测</li>
 *   <li>{@link DecisionTableEvalProvider} - 决策表评估</li>
 *   <li>{@link RuleChainGraphProvider} - 规则链画布</li>
 *   <li>{@link GraphExecutionProvider} - 画布执行</li>
 *   <li>{@link RuleDependencyProvider} - 规则依赖关系</li>
 *   <li>{@link RuleCategoryProvider} - 规则目录树</li>
 *   <li>{@link ABTestAutoRollbackProvider} - AB Test 自动回滚</li>
 *   <li>{@link RulePackProvider} - 规则集市场</li>
 * </ul>
 *
 * @author ydsz-pmis-team
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
    /** 规则模板服务（SPI，由 project 模块提供实现） */
    private final RuleTemplateProvider ruleTemplateProvider;
    /** 规则冲突检测器（SPI，由 project 模块提供实现） */
    private final RuleConflictDetectorProvider ruleConflictDetectorProvider;
    /** 规则测试用例 Mapper */
    private final RuleTestCaseMapper ruleTestCaseMapper;
    /** 规则执行轨迹 Mapper */
    private final RuleExecutionTraceMapper ruleExecutionTraceMapper;
    /** 决策表 Mapper */
    private final DecisionTableMapper decisionTableMapper;
    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;
    /** 决策表评估服务（SPI，由 project 模块提供实现） */
    private final DecisionTableEvalProvider decisionTableEvalProvider;
    /** 表达式校验服务 */
    private final ExpressionValidationService expressionValidationService;
    /** 规则链图服务（SPI，由 project 模块提供实现） */
    private final RuleChainGraphProvider ruleChainGraphProvider;
    /** 图执行服务（SPI，由 project 模块提供实现） */
    private final GraphExecutionProvider graphExecutionProvider;
    /** 规则依赖服务（SPI，由 project 模块提供实现） */
    private final RuleDependencyProvider ruleDependencyProvider;
    /** 规则分类树服务（SPI，由 project 模块提供实现） */
    private final RuleCategoryProvider ruleCategoryProvider;
    /** A/B 测试自动回滚服务（SPI，由 project 模块提供实现） */
    private final ABTestAutoRollbackProvider abTestAutoRollbackProvider;
    /** 规则包服务（SPI，由 project 模块提供实现） */
    private final RulePackProvider rulePackProvider;
    // 规则压测服务（P2-9）：可选注入，RuleAdminService 未装配时为空
    private final ObjectProvider<RuleStressTestService> ruleStressTestServiceProvider;
    // 决策表管理服务（P0-3）：可选注入，未启用决策表时为空
    private final ObjectProvider<DecisionTableAdminService> decisionTableAdminServiceProvider;
    // 多级审批流服务（P1-3）：可选注入，未配置 RuleConfigProvider 时为空
    private final ObjectProvider<RuleApprovalService> ruleApprovalServiceProvider;

    /**
     * 查询全部规则定义
     *
     * @return 规则定义列表
     */
    @GetMapping
    public BaseResponse<List<RuleDefinition>> list() {
        return BaseResponse.ok(ruleAdminService.listAll());
    }

    /**
     * 查询单条规则定义
     *
     * @param ruleCode 规则编码
     * @return 规则定义
     */
    @GetMapping("/{ruleCode}")
    public BaseResponse<RuleDefinition> get(@PathVariable String ruleCode) {
        return BaseResponse.ok(ruleAdminService.getByCode(ruleCode));
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
    @PostMapping
    @AuthApiPermission(apiCodes = "execution:rule:save")
    public BaseResponse<RuleDefinition> save(@RequestBody RuleDefinition definition,
                                        @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator,
                                        @RequestParam(value = "changeDesc", defaultValue = "API 更新") String changeDesc) {
        return BaseResponse.ok(ruleAdminService.save(definition, operator, changeDesc));
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
    @PutMapping("/{ruleCode}/toggle")
    @AuthApiPermission(apiCodes = "execution:rule:toggle")
    public BaseResponse<Void> toggle(@PathVariable String ruleCode,
                                @RequestParam boolean enabled,
                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.toggle(ruleCode, enabled, operator);
        return BaseResponse.ok();
    }

    /**
     * 查询规则版本历史
     *
     * @param ruleCode 规则编码
     * @return 版本历史
     */
    @GetMapping("/{ruleCode}/versions")
    public BaseResponse<List<RuleVersion>> listVersions(@PathVariable String ruleCode) {
        return BaseResponse.ok(ruleAdminService.listVersions(ruleCode));
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
     * @since 2.0.0
     */
    @GetMapping("/{ruleCode}/versionDiff")
    public BaseResponse<RuleVersionDiff> versionDiff(@PathVariable String ruleCode,
                                                @RequestParam int oldVersion,
                                                @RequestParam int newVersion) {
        List<RuleVersion> versions = ruleAdminService.listVersions(ruleCode);
        RuleVersion oldV = versions.stream().filter(v -> v.getVersion() == oldVersion).findFirst().orElse(null);
        RuleVersion newV = versions.stream().filter(v -> v.getVersion() == newVersion).findFirst().orElse(null);

        if (oldV == null || newV == null) {
            return BaseResponse.fail("版本不存在: oldVersion=" + oldVersion + ", newVersion=" + newVersion);
        }

        try {
            RuleDefinition oldDef = JsonUtils.fromJson(oldV.getDefinitionJson(), RuleDefinition.class);
            RuleDefinition newDef = JsonUtils.fromJson(newV.getDefinitionJson(), RuleDefinition.class);
            RuleVersionDiffService diffService = new RuleVersionDiffService();
            return BaseResponse.ok(diffService.diff(oldDef, newDef));
        } catch (Exception e) {
            log.error("[LiteRule] 版本 Diff 失败: ruleCode={}, oldV={}, newV={}", ruleCode, oldVersion, newVersion, e);
            return BaseResponse.fail("版本 Diff 解析失败: " + e.getMessage());
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
    @PostMapping("/{ruleCode}/rollback")
    public BaseResponse<RuleDefinition> rollback(@PathVariable String ruleCode,
                                            @RequestParam int version,
                                            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.ok(ruleAdminService.rollback(ruleCode, version, operator));
    }

    /**
     * Dry-run 仿真
     *
     * @param ruleCode 规则编码（可选，null 仿真全部规则）
     * @param facts    事实数据
     * @return 仿真结果
     */
    @Idempotent(key = "ruleAdmin:dryRun", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/dryRun")
    public BaseResponse<List<RuleResult>> dryRun(@RequestParam(required = false) String ruleCode,
                                            @RequestBody Map<String, Object> facts) {
        return BaseResponse.ok(ruleAdminService.dryRun(ruleCode, facts));
    }

    /**
     * 校验表达式语法
     *
     * @param expression 表达式
     * @return true=合法
     */
    @GetMapping("/validate")
    public BaseResponse<Boolean> validate(@RequestParam String expression) {
        return BaseResponse.ok(ruleAdminService.validateExpression(expression));
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
    @PostMapping("/exprTrace")
    public BaseResponse<ExpressionEvaluator.TraceResult> traceExpression(@RequestBody Map<String, Object> request) {
        String expression = (String) request.get("expression");
        Map<String, Object> facts = new HashMap<>();
        Object raw = request.get("facts");
        if (raw instanceof Map<?, ?> rawMap) {
            rawMap.forEach((k, v) -> facts.put(String.valueOf(k), v));
        }
        return BaseResponse.ok(ruleAdminService.traceExpression(expression, facts));
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
    @PostMapping("/validateExpression")
    public BaseResponse<ExpressionValidationResult> validateExpression(@Valid @RequestBody ExpressionValidateDTO dto) {
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
        return BaseResponse.ok(result);
    }

    /**
     * 批量校验表达式（1.4.0 起支持）
     *
     * @param request key=标签，value=表达式
     * @return 校验结果（与输入顺序一致）
     */
    @Idempotent(key = "ruleAdmin:validateBatch", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/validateBatch")
    public BaseResponse<Map<String, ExpressionValidationResult>> validateBatch(@RequestBody Map<String, String> request) {
        return BaseResponse.ok(expressionValidationService.validateBatch(request));
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
    @PostMapping("/{ruleCode}/abTest")
    public BaseResponse<ABTestService.ABTestReport> abTest(@PathVariable String ruleCode,
                                                      @Valid @RequestBody RuleABTestDTO dto) {
        RuleDefinition currentDef = ruleAdminService.getByCode(ruleCode);
        if (currentDef == null) {
            return BaseResponse.fail("规则不存在: " + ruleCode);
        }

        // 构建候选规则定义（基于当前规则，覆盖候选字段）
        RuleDefinition candidateDef = dto.getCandidate();
        candidateDef.setCode(ruleCode);

        return BaseResponse.ok(abTestService.test(currentDef, candidateDef, dto.getFacts()));
    }

    /**
     * 查询规则引擎执行统计
     *
     * @return 统计快照
     */
    @GetMapping("/stats")
    public BaseResponse<RuleEngineStats> stats() {
        return BaseResponse.ok(ruleEngine.getStats());
    }

    // ==================== 规则模板市场 ====================

    /**
     * 查询全部规则模板
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    public BaseResponse<List<RuleTemplateDO>> listTemplates() {
        return BaseResponse.ok(ruleTemplateProvider.listAll());
    }

    /**
     * 按类别查询规则模板
     *
     * @param category 模板类别
     * @return 模板列表
     */
    @GetMapping("/templates/category/{category}")
    public BaseResponse<List<RuleTemplateDO>> listTemplatesByCategory(@PathVariable String category) {
        return BaseResponse.ok(ruleTemplateProvider.listByCategory(category));
    }

    /**
     * 按行业查询规则模板
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    @GetMapping("/templates/industry/{industry}")
    public BaseResponse<List<RuleTemplateDO>> listTemplatesByIndustry(@PathVariable String industry) {
        return BaseResponse.ok(ruleTemplateProvider.listByIndustry(industry));
    }

    /**
     * 一键导入模板为规则定义
     *
     * @param templateCode 模板编码
     * @param operator     操作人（从 Header 获取）
     * @return 保存后的规则定义
     */
    @Idempotent(key = "ruleAdmin:importTemplate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/templates/{templateCode}/import")
    public BaseResponse<RuleDefinition> importTemplate(@PathVariable String templateCode,
                                                  @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.ok(ruleTemplateProvider.importTemplate(templateCode, operator));
    }

    // ==================== 冲突检测 ====================

    /**
     * 检测规则冲突
     *
     * @return 冲突规则对列表
     */
    @GetMapping("/conflicts")
    public BaseResponse<List<RuleConflictInfo>> detectConflicts() {
        return BaseResponse.ok(ruleConflictDetectorProvider.detectConflicts());
    }

    // ==================== 测试用例管理 ====================

    /**
     * 查询测试用例（可选按规则编码过滤）
     *
     * @param ruleCode 规则编码（可选）
     * @return 测试用例列表
     */
    @GetMapping("/testCases")
    public BaseResponse<List<RuleTestCaseDO>> listTestCases(@RequestParam(required = false) String ruleCode) {
        LambdaQueryWrapper<RuleTestCaseDO> wrapper = new LambdaQueryWrapper<>();
        if (ruleCode != null && !ruleCode.isBlank()) {
            wrapper.eq(RuleTestCaseDO::getRuleCode, ruleCode);
        }
        wrapper.orderByDesc(RuleTestCaseDO::getUpdatedAt);
        return BaseResponse.ok(ruleTestCaseMapper.selectList(wrapper));
    }

    /**
     * 保存测试用例
     *
     * @param testCase 测试用例
     * @return 保存后的测试用例
     */
    @Idempotent(key = "ruleAdmin:saveTestCase", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/testCases")
    public BaseResponse<RuleTestCaseDO> saveTestCase(@RequestBody RuleTestCaseDO testCase) {
        if (testCase.getId() != null) {
            ruleTestCaseMapper.updateById(testCase);
        } else {
            ruleTestCaseMapper.insert(testCase);
        }
        return BaseResponse.ok(testCase);
    }

    /**
     * 删除测试用例
     *
     * @param id 测试用例 ID
     * @return 操作结果
     */
    @OperationLog(module = "规则引擎", action = "删除测试用例", bizType = "RULE_TEST_CASE")
    @Idempotent(key = "ruleAdmin:deleteTestCase", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/testCases/{id}")
    public BaseResponse<Void> deleteTestCase(@PathVariable String id) {
        ruleTestCaseMapper.deleteById(id);
        return BaseResponse.ok();
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
    @Idempotent(key = "ruleAdmin:batchRunTestCases", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/testCases/batchRun")
    public BaseResponse<Map<String, Object>> batchRunTestCases(@Valid @RequestBody TestCaseBatchRunDTO dto) {
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
            return BaseResponse.ok(Map.of("total", 0, "passed", 0, "failed", 0, "passRate", "100%"));
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

        return BaseResponse.ok(report);
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
    @Idempotent(key = "ruleAdmin:changeStatus", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{ruleCode}/status")
    @AuthApiPermission(apiCodes = "execution:rule:status")
    public BaseResponse<RuleDefinition> changeStatus(@PathVariable String ruleCode,
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
        return BaseResponse.ok(ruleAdminService.save(def, operator, "状态变更: " + current.getDesc() + " -> " + target.getDesc()));
    }

    /**
     * 审批通过（1.4.0 起支持）
     *
     * <p>将规则从 DRAFT/REVIEW 状态变更为 PUBLISHED，并记录审批人、审批时间、审批意见。
     * 主要用于规则审批闭环：新建 → DRAFT → 人工审批 → PUBLISHED。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 comment（审批意见）
     * @param operator 审批人
     * @return 审批后的规则定义
     */
    @Idempotent(key = "ruleAdmin:approve", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/approve")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<RuleDefinition> approve(@PathVariable String ruleCode,
                                           @Valid @RequestBody RuleApproveDTO dto,
                                           @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.fail("规则不存在: " + ruleCode);
        }

        RuleStatus current = parseStatusSafely(def.getStatus());
        if (!current.canTransitionTo(RuleStatus.PUBLISHED)) {
            return BaseResponse.fail("当前状态 " + current.getDesc() + " 不允许审批通过，仅 DRAFT/REVIEW 可审批");
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
        return BaseResponse.ok(ruleAdminService.save(def, operator, changeDesc));
    }

    /**
     * 审批驳回（1.4.0 起支持）
     *
     * <p>将规则从 DRAFT/REVIEW 状态变更为 ARCHIVED，并记录驳回理由。
     * 主要用于规则审批闭环：新建 → DRAFT → 人工驳回 → ARCHIVED。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 reason（驳回理由，必填）
     * @param operator 审批人
     * @return 驳回后的规则定义
     */
    @Idempotent(key = "ruleAdmin:reject", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/reject")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<RuleDefinition> reject(@PathVariable String ruleCode,
                                          @Valid @RequestBody RuleRejectDTO dto,
                                          @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.fail("规则不存在: " + ruleCode);
        }

        RuleStatus current = parseStatusSafely(def.getStatus());
        if (!current.canTransitionTo(RuleStatus.ARCHIVED)) {
            return BaseResponse.fail("当前状态 " + current.getDesc() + " 不允许驳回，仅 DRAFT/REVIEW/PUBLISHED 可驳回");
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
        return BaseResponse.ok(ruleAdminService.save(def, operator, changeDesc));
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
    @Idempotent(key = "ruleAdmin:submitReview", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/submitReview")
    @AuthApiPermission(apiCodes = "execution:rule:save")
    @OperationLog(module = "规则引擎", action = "提交审核", bizType = "RULE")
    public BaseResponse<ApprovalRecord> submitReview(@PathVariable String ruleCode,
                                                @Valid @RequestBody(required = false) RuleSubmitReviewDTO dto,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("多级审批流服务未启用");
        }
        String flowCode = dto == null ? null : dto.getFlowCode();
        return BaseResponse.ok(svc.submitForReview(ruleCode, flowCode, operator));
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
    @Idempotent(key = "ruleAdmin:approveLevel", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/approveLevel")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    @OperationLog(module = "规则引擎", action = "级别审批通过", bizType = "RULE")
    public BaseResponse<ApprovalRecord> approveLevel(@PathVariable String ruleCode,
                                                @Valid @RequestBody RuleApproveDTO dto,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("多级审批流服务未启用");
        }
        String comment = dto.getComment() == null ? "" : dto.getComment();
        return BaseResponse.ok(svc.approve(ruleCode, operator, comment));
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
    @Idempotent(key = "ruleAdmin:rejectLevel", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/rejectLevel")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    @OperationLog(module = "规则引擎", action = "级别审批驳回", bizType = "RULE")
    public BaseResponse<ApprovalRecord> rejectLevel(@PathVariable String ruleCode,
                                               @Valid @RequestBody RuleRejectDTO dto,
                                               @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("多级审批流服务未启用");
        }
        return BaseResponse.ok(svc.reject(ruleCode, operator, dto.getReason()));
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
    @Idempotent(key = "ruleAdmin:delegate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/delegate")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    @OperationLog(module = "规则引擎", action = "委托审批", bizType = "RULE")
    public BaseResponse<ApprovalRecord> delegate(@PathVariable String ruleCode,
                                            @Valid @RequestBody RuleDelegateDTO dto,
                                            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("多级审批流服务未启用");
        }
        String comment = dto.getComment() == null ? "" : dto.getComment();
        return BaseResponse.ok(svc.delegate(ruleCode, operator, dto.getDelegatedTo(), comment));
    }

    /**
     * 查询审批状态（P1-3 多级审批流）
     *
     * @param ruleCode 规则编码
     * @return 审批记录；无审批记录时返回 null
     */
    @GetMapping("/{ruleCode}/approvalStatus")
    public BaseResponse<ApprovalRecord> approvalStatus(@PathVariable String ruleCode) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.ok(null);
        }
        return BaseResponse.ok(svc.getApprovalStatus(ruleCode));
    }

    /**
     * 查询待审批列表（P1-3 多级审批流）
     *
     * @param approver 审批人工号
     * @return 待审批记录列表
     */
    @GetMapping("/pendingApprovals")
    public BaseResponse<List<ApprovalRecord>> pendingApprovals(@RequestParam String approver) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.ok(List.of());
        }
        return BaseResponse.ok(svc.listPendingApprovals(approver));
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
    @Idempotent(key = "ruleAdmin:cancelReview", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/cancelReview")
    @AuthApiPermission(apiCodes = "execution:rule:save")
    @OperationLog(module = "规则引擎", action = "撤回审核", bizType = "RULE")
    public BaseResponse<ApprovalRecord> cancelReview(@PathVariable String ruleCode,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("多级审批流服务未启用");
        }
        return BaseResponse.ok(svc.cancelReview(ruleCode, operator));
    }

    /**
     * 查询可用审批流配置（P1-3 多级审批流）
     *
     * @return 审批流配置列表
     */
    @GetMapping("/approvalFlows")
    public BaseResponse<List<ApprovalFlow>> approvalFlows() {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.ok(List.of());
        }
        return BaseResponse.ok(svc.listFlows());
    }

    // ==================== 执行链路追踪 ====================

    /**
     * 按 traceId 查询执行链路
     */
    @GetMapping("/traces/{traceId}")
    public BaseResponse<List<RuleExecutionTraceDO>> getTrace(@PathVariable String traceId) {
        return BaseResponse.ok(ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .eq(RuleExecutionTraceDO::getTraceId, traceId)
                .orderByAsc(RuleExecutionTraceDO::getCreatedAt)));
    }

    /**
     * 按规则编码查询最近链路
     */
    @GetMapping("/traces/rule/{ruleCode}")
    public BaseResponse<List<RuleExecutionTraceDO>> getTracesByRule(@PathVariable String ruleCode,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(ruleExecutionTraceMapper.selectList(
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
    @Idempotent(key = "ruleAdmin:replayTrace", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/traces/{traceId}/replay")
    public BaseResponse<Map<String, Object>> replayTrace(@PathVariable String traceId) {
        List<RuleExecutionTraceDO> traces = ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .eq(RuleExecutionTraceDO::getTraceId, traceId)
                .orderByAsc(RuleExecutionTraceDO::getCreatedAt));

        if (traces.isEmpty()) {
            return BaseResponse.fail("未找到 traceId=" + traceId + " 的执行记录");
        }

        // 取第一条 trace 的 factsSnapshot 作为回放输入
        Map<String, Object> facts = traces.get(0).getFactsSnapshot();
        if (facts == null || facts.isEmpty()) {
            return BaseResponse.fail("traceId=" + traceId + " 的事实快照为空，无法回放");
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

        return BaseResponse.ok(replay);
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
    @Idempotent(key = "ruleAdmin:batchReplayTraces", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/traces/batchReplay")
    public BaseResponse<Map<String, Object>> batchReplayTraces(@RequestBody Map<String, Object> request) {
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
            return BaseResponse.fail("startTime 和 endTime 不能为空");
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

        return BaseResponse.ok(report);
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
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/{ruleCode}/impactPreview")
    public BaseResponse<Map<String, Object>> impactPreview(@PathVariable String ruleCode,
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
            return BaseResponse.fail("conditionExpression 不能为空");
        }

        // 解析默认严重度
        RuleSeverity defaultSeverity = null;
        if (defaultSeverityStr != null && !defaultSeverityStr.isBlank()) {
            try {
                defaultSeverity = RuleSeverity.valueOf(defaultSeverityStr);
            } catch (IllegalArgumentException e) {
                return BaseResponse.fail("非法的 defaultSeverity: " + defaultSeverityStr
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

        return BaseResponse.ok(report);
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
    public BaseResponse<List<RuleExecutionTraceDO>> listRecentTraces(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTraceDO>()
                .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                .last("LIMIT " + limit)));
    }

    // ==================== 决策表管理 ====================

    /**
     * 查询全部决策表
     */
    @GetMapping("/decisionTables")
    public BaseResponse<List<DecisionTableDO>> listDecisionTables() {
        return BaseResponse.ok(decisionTableMapper.selectList(null));
    }

    /**
     * 查询单条决策表
     */
    @GetMapping("/decisionTables/{tableCode}")
    public BaseResponse<DecisionTableDO> getDecisionTable(@PathVariable String tableCode) {
        DecisionTableDO dt = decisionTableMapper.selectOne(
            new LambdaQueryWrapper<DecisionTableDO>().eq(DecisionTableDO::getTableCode, tableCode));
        return BaseResponse.ok(dt);
    }

    /**
     * 保存决策表
     */
    @Idempotent(key = "ruleAdmin:saveDecisionTable", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/decisionTables")
    public BaseResponse<DecisionTableDO> saveDecisionTable(@RequestBody DecisionTableDO decisionTable) {
        if (decisionTable.getId() != null) {
            decisionTableMapper.updateById(decisionTable);
        } else {
            decisionTableMapper.insert(decisionTable);
        }
        return BaseResponse.ok(decisionTable);
    }

    /**
     * 删除决策表
     */
    @OperationLog(module = "规则引擎", action = "删除决策表", bizType = "DECISION_TABLE")
    @Idempotent(key = "ruleAdmin:deleteDecisionTable", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/decisionTables/{id}")
    public BaseResponse<Void> deleteDecisionTable(@PathVariable String id) {
        decisionTableMapper.deleteById(id);
        return BaseResponse.ok();
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
    @Idempotent(key = "ruleAdmin:evaluateDecisionTable", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/decisionTables/{tableCode}/evaluate")
    public BaseResponse<List<Map<String, Object>>> evaluateDecisionTable(@PathVariable String tableCode,
                                                                   @RequestBody Map<String, Object> facts) {
        try {
            return BaseResponse.ok(decisionTableEvalProvider.evaluate(tableCode, facts));
        } catch (Exception e) {
            log.warn("[DecisionTable] 评估失败: tableCode={}, err={}", tableCode, e.getMessage());
            return BaseResponse.fail(e.getMessage());
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
    @GetMapping("/decisionTables/{tableCode}/exportExcel")
    @AuthApiPermission(apiCodes = "execution:rule:view")
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
    @PostMapping(value = "/decisionTables/importExcel", consumes = "multipart/form-data")
    @AuthApiPermission(apiCodes = "execution:rule:save")
    public BaseResponse<DecisionTableDefinition> importDecisionTableExcel(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("决策表管理服务未启用");
        }
        if (file == null || file.isEmpty()) {
            return BaseResponse.fail("上传文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            DecisionTableDefinition saved = svc.importExcel(bytes, operator);
            return BaseResponse.ok(saved);
        } catch (IllegalArgumentException e) {
            log.warn("[DecisionTable] Excel 导入失败: {}", e.getMessage());
            return BaseResponse.fail(e.getMessage());
        } catch (IOException e) {
            log.warn("[DecisionTable] Excel 文件读取失败: {}", e.getMessage());
            return BaseResponse.fail("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 下载决策表 Excel 空白模板（P0-3）
     *
     * <p>返回预填充列结构的 .xlsx 模板，用户填写后通过 /import-excel 上传。
     *
     * @return xlsx 模板文件流
     */
    @GetMapping("/decisionTables/excelTemplate")
    @AuthApiPermission(apiCodes = "execution:rule:view")
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
    public BaseResponse<Map<String, Object>> exportRules() {
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
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportTime", LocalDateTime.now().toString());
        result.put("ruleCount", rules.size());
        result.put("rules", exportData);
        return BaseResponse.ok(result);
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
    @Idempotent(key = "ruleAdmin:importRules", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/import")
    public BaseResponse<Map<String, Object>> importRules(@Valid @RequestBody RuleImportDTO dto,
                                                    @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<Map<String, Object>> rules = dto.getRules();
        if (rules == null || rules.isEmpty()) {
            return BaseResponse.ok(Map.of("imported", 0, "skipped", 0));
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
        return BaseResponse.ok(result);
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
    @Idempotent(key = "ruleAdmin:deleteRule", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{ruleCode}")
    @AuthApiPermission(apiCodes = "execution:rule:delete")
    public BaseResponse<Void> deleteRule(@PathVariable String ruleCode,
                                   @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.fail("规则不存在: " + ruleCode);
        }
        def.setStatus(RuleStatus.ARCHIVED.name());
        def.setEnabled(false);
        ruleAdminService.save(def, operator, "[删除] 软删除规则 status=ARCHIVED");
        // 同步删除画布
        ruleChainGraphProvider.delete(ruleCode);
        log.info("[LiteRule] 规则已删除: ruleCode={}, operator={}", ruleCode, operator);
        return BaseResponse.ok();
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
    @Idempotent(key = "ruleAdmin:batchToggle", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batchToggle")
    @AuthApiPermission(apiCodes = "execution:rule:toggle")
    public BaseResponse<Map<String, Object>> batchToggle(@Valid @RequestBody RuleBatchToggleDTO dto,
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
        return BaseResponse.ok(result);
    }

    /**
     * 批量调整规则优先级
     *
     * @param request  请求体，包含 ruleCodes / delta（可为负）
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @Idempotent(key = "ruleAdmin:batchPriority", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batchPriority")
    public BaseResponse<Map<String, Object>> batchPriority(@Valid @RequestBody RuleBatchPriorityDTO dto,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        List<String> ruleCodes = dto.getRuleCodes();
        Integer delta = dto.getDelta();
        // @NotEmpty + @NotNull 已校验非空；delta==0 需保留手动校验（JSR-303 无原生非零约束）
        if (delta == 0) {
            return BaseResponse.fail("delta 不能为 0");
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
        return BaseResponse.ok(result);
    }

    /**
     * 批量调整规则分类
     *
     * @param request  请求体，包含 ruleCodes / category
     * @param operator 操作人
     * @return 成功与失败明细
     */
    @Idempotent(key = "ruleAdmin:batchCategory", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/batchCategory")
    public BaseResponse<Map<String, Object>> batchCategory(@Valid @RequestBody RuleBatchCategoryDTO dto,
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
        return BaseResponse.ok(result);
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
    public BaseResponse<RuleChainGraph> getChainGraph(@PathVariable String ruleCode) {
        return BaseResponse.ok(ruleChainGraphProvider.getByRuleCode(ruleCode));
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
    @Idempotent(key = "ruleAdmin:saveChainGraph", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/graph")
    public BaseResponse<Map<String, Object>> saveChainGraph(@PathVariable String ruleCode,
                                                       @Valid @RequestBody RuleChainGraph graph,
                                                       @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        // 1. 结构校验
        List<RuleGraphValidator.GraphValidationIssue> issues = RuleGraphValidator.validate(graph);
        if (!RuleGraphValidator.isValid(issues)) {
            return BaseResponse.ok(Map.of(
                    "valid", false,
                    "issues", issues,
                    "message", "画布结构不合法，请先修复错误"
            ));
        }
        // 2. 保存
        RuleChainGraph saved = ruleChainGraphProvider.save(ruleCode, graph, operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("issues", issues);
        result.put("graph", saved);
        return BaseResponse.ok(result);
    }

    /**
     * 删除画布
     */
    @OperationLog(module = "规则引擎", action = "删除画布", bizType = "RULE_CHAIN_GRAPH")
    @Idempotent(key = "ruleAdmin:deleteChainGraph", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{ruleCode}/graph")
    public BaseResponse<Void> deleteChainGraph(@PathVariable String ruleCode) {
        ruleChainGraphProvider.delete(ruleCode);
        return BaseResponse.ok();
    }

    /**
     * 校验画布结构（不保存）
     *
     * <p>供前端"实时校验"按钮调用，返回 ERROR/WARN 两级问题。
     */
    @PostMapping("/{ruleCode}/graph/validate")
    public BaseResponse<List<RuleGraphValidator.GraphValidationIssue>> validateChainGraph(@RequestBody RuleChainGraph graph) {
        return BaseResponse.ok(RuleGraphValidator.validate(graph));
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
    @PostMapping("/expressionPreview")
    public BaseResponse<ExpressionPreviewResult> previewExpression(
            @RequestParam String expression,
            @RequestBody Map<String, Object> facts) {
        return BaseResponse.ok(expressionValidationService.previewEvaluate(expression, facts));
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
    @Idempotent(key = "ruleAdmin:dryRunGraph", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/graph/dryRun")
    public BaseResponse<List<RuleResult>> dryRunGraph(@PathVariable String ruleCode,
                                                 @RequestBody Map<String, Object> facts) {
        try {
            List<RuleResult> results = graphExecutionProvider.dryRunGraph(ruleCode, facts);
            return BaseResponse.ok(results);
        } catch (IllegalArgumentException e) {
            log.warn("[RuleAdmin] 画布 dry-run 失败: ruleCode={}, err={}", ruleCode, e.getMessage());
            return BaseResponse.fail(e.getMessage());
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
    @GetMapping("/{ruleCode}/graph/invalidRefs")
    public BaseResponse<List<String>> invalidGraphRefs(@PathVariable String ruleCode) {
        return BaseResponse.ok(graphExecutionProvider.collectInvalidReferences(ruleCode));
    }

    // ==================== 函数市场（P1-7） ====================

    /**
     * 获取已注册表达式函数列表
     *
     * <p>P1-7 函数市场：前端 CodeMirror 编辑器拉取此接口，渲染自动补全 + 悬浮文档。
     * 当前默认返回 18 个内置函数（string/math/convert/datetime/logic/type 六类）。
     *
     * @param engine 引擎类型（liteexpr/all），默认 all
     * @return 函数定义列表
     */
    @GetMapping("/expressionFunctions")
    public BaseResponse<List<ExpressionFunctionDef>> expressionFunctions(
            @RequestParam(value = "engine", defaultValue = "all") String engine) {
        List<ExpressionFunctionDef> all = ExpressionFunctionDef.defaults();
        List<ExpressionFunctionDef> filtered = all.stream()
                .filter(f -> "all".equalsIgnoreCase(engine)
                        || engine.equalsIgnoreCase(f.getSupportedEngines()))
                .toList();
        return BaseResponse.ok(filtered);
    }

    // ==================== 规则依赖（P1-8） ====================

    /**
     * 添加规则依赖
     */
    @Idempotent(key = "ruleAdmin:addDependency", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/dependencies")
    public BaseResponse<RuleDependencyDO> addDependency(
            @PathVariable String ruleCode,
            @Valid @RequestBody RuleDependencyAddDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        String dependsOn = dto.getDependsOnRuleCode();
        String depType = dto.getDependencyType() == null ? "EXECUTE" : dto.getDependencyType();
        Boolean cascade = dto.getCascadeOnDisable() == null ? false : dto.getCascadeOnDisable();
        String description = dto.getDescription();
        return BaseResponse.ok(ruleDependencyProvider.add(ruleCode, dependsOn, depType, cascade, description, operator));
    }

    /**
     * 删除规则依赖
     */
    @OperationLog(module = "规则引擎", action = "删除规则依赖", bizType = "RULE_DEPENDENCY")
    @Idempotent(key = "ruleAdmin:removeDependency", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{ruleCode}/dependencies/{dependsOnRuleCode}")
    public BaseResponse<Void> removeDependency(
            @PathVariable String ruleCode,
            @PathVariable String dependsOnRuleCode) {
        ruleDependencyProvider.remove(ruleCode, dependsOnRuleCode);
        return BaseResponse.ok();
    }

    /**
     * 查询规则的依赖（正向：依赖了哪些）
     */
    @GetMapping("/{ruleCode}/dependencies")
    public BaseResponse<List<RuleDependencyDO>> listDependencies(@PathVariable String ruleCode) {
        return BaseResponse.ok(ruleDependencyProvider.listDependencies(ruleCode));
    }

    /**
     * 查询被依赖（反向：被哪些规则依赖）
     */
    @GetMapping("/{ruleCode}/dependents")
    public BaseResponse<List<RuleDependencyDO>> listDependents(@PathVariable String ruleCode) {
        return BaseResponse.ok(ruleDependencyProvider.listDependents(ruleCode));
    }

    /**
     * 查询级联禁用影响（disable ruleCode 时，需要级联禁用的规则列表）
     */
    @GetMapping("/{ruleCode}/cascadingDisable")
    public BaseResponse<List<String>> cascadingDisable(@PathVariable String ruleCode) {
        return BaseResponse.ok(ruleDependencyProvider.cascadingDisable(ruleCode));
    }

    // ==================== 规则目录树 + 责任人（P1-9） ====================

    /**
     * 获取规则目录树
     *
     * <p>树根为虚拟 ROOT，children 为一级分类。叶子节点或中间节点都包含该路径下的规则数与 Owner 列表。
     */
    @GetMapping("/categoryTree")
    public BaseResponse<CategoryNode> categoryTree() {
        return BaseResponse.ok(ruleCategoryProvider.buildTree());
    }

    /**
     * 按分类路径前缀查询规则
     *
     * @param path 分类路径前缀，例如 "finance" / "finance/credit"
     */
    @GetMapping("/byCategoryPath")
    public BaseResponse<List<RuleDefinition>> listByCategoryPath(
            @RequestParam(value = "path", required = false) String path) {
        return BaseResponse.ok(ruleCategoryProvider.listDefinitionsByCategoryPath(path));
    }

    /**
     * 按 Owner 查询规则
     */
    @GetMapping("/byOwner")
    public BaseResponse<List<RuleDefinition>> listByOwner(
            @RequestParam(value = "owner") String owner) {
        return BaseResponse.ok(ruleCategoryProvider.listDefinitionsByOwner(owner));
    }

    /**
     * 设置规则责任人
     */
    @Idempotent(key = "ruleAdmin:setOwner", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{ruleCode}/owner")
    public BaseResponse<Void> setOwner(
            @PathVariable String ruleCode,
            @RequestParam(value = "owner") String owner,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.updateOwner(ruleCode, owner, operator);
        return BaseResponse.ok();
    }

    /**
     * 设置规则分类路径
     */
    @Idempotent(key = "ruleAdmin:setCategoryPath", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{ruleCode}/categoryPath")
    public BaseResponse<Void> setCategoryPath(
            @PathVariable String ruleCode,
            @RequestParam(value = "path") String path,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        ruleAdminService.updateCategoryPath(ruleCode, path, operator);
        return BaseResponse.ok();
    }

    // ==================== AB Test 自动回滚策略（P1-10） ====================

    /**
     * 获取规则的 AB Test 自动回滚策略（无配置时返回默认策略）
     */
    @GetMapping("/{ruleCode}/abPolicy")
    public BaseResponse<RuleABPolicyDO> getABPolicy(@PathVariable String ruleCode) {
        RuleABPolicyDO policy = abTestAutoRollbackProvider.getPolicy(ruleCode);
        return BaseResponse.ok(policy);
    }

    /**
     * 更新规则的 AB Test 自动回滚策略
     */
    @Idempotent(key = "ruleAdmin:updateAbpolicy", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{ruleCode}/abPolicy")
    public BaseResponse<Void> updateABPolicy(
            @PathVariable String ruleCode,
            @Valid @RequestBody RuleABPolicyDO policy,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        policy.setRuleCode(ruleCode);
        abTestAutoRollbackProvider.savePolicy(policy, operator);
        return BaseResponse.ok();
    }

    /**
     * 查询规则的回滚历史
     */
    @GetMapping("/{ruleCode}/abRollbacks")
    public BaseResponse<List<RuleABRollbackDO>> listRollbackHistory(@PathVariable String ruleCode) {
        return BaseResponse.ok(abTestAutoRollbackProvider.listRollbackHistory(ruleCode));
    }

    /**
     * 主动触发 AB Test 评估（人工立即检查）
     */
    @Idempotent(key = "ruleAdmin:evaluateAb", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/abEvaluate")
    public BaseResponse<Boolean> evaluateAB(@PathVariable String ruleCode) {
        return BaseResponse.ok(abTestAutoRollbackProvider.evaluateOne(ruleCode));
    }

    /**
     * 人工回滚（Owner 主动请求 / 紧急操作）
     *
     * @param reason MANUAL / OWNER_REQUEST
     */
    @Idempotent(key = "ruleAdmin:manualRollback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/abRollback")
    public BaseResponse<RuleABRollbackDO> manualRollback(
            @PathVariable String ruleCode,
            @RequestParam(value = "reason", defaultValue = "MANUAL") String reason,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.ok(abTestAutoRollbackProvider.manualRollback(ruleCode, operator, reason));
    }

    // ==================== 规则集市场（P2-14） ====================

    /**
     * 列出全部规则集（市场首页）
     */
    @GetMapping("/packs")
    public BaseResponse<List<RulePack>> listPacks() {
        return BaseResponse.ok(rulePackProvider.listAll());
    }

    /**
     * 搜索规则集
     */
    @GetMapping("/packs/search")
    public BaseResponse<List<RulePack>> searchPacks(@RequestParam(value = "keyword", required = false) String keyword) {
        return BaseResponse.ok(rulePackProvider.search(keyword));
    }

    /**
     * 查询规则集最新版本
     */
    @GetMapping("/packs/{packCode}/latest")
    public BaseResponse<RulePack> getLatestPack(@PathVariable String packCode) {
        return BaseResponse.ok(rulePackProvider.getLatest(packCode));
    }

    /**
     * 查询规则集的所有版本
     */
    @GetMapping("/packs/{packCode}/versions")
    public BaseResponse<List<RulePack>> listPackVersions(@PathVariable String packCode) {
        return BaseResponse.ok(rulePackProvider.listVersions(packCode));
    }

    /**
     * 查询规则集指定版本（含规则定义快照，P2-8）
     */
    @GetMapping("/packs/{packCode}/versions/{version}")
    public BaseResponse<RulePack> getPackVersion(
            @PathVariable String packCode,
            @PathVariable String version) {
        return BaseResponse.ok(rulePackProvider.getVersion(packCode, version));
    }

    /**
     * 知识包版本回滚（P2-8）：将该版本固化的规则定义整体恢复到在线规则表
     */
    @PostMapping("/packs/{packCode}/rollback")
    @OperationLog(module = "规则引擎", action = "知识包回滚", bizType = "RULE_PACK")
    public BaseResponse<InstallResult> rollbackPack(
            @PathVariable String packCode,
            @RequestParam(value = "version") String version,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.ok(rulePackProvider.rollback(packCode, version, operator));
    }

    /**
     * 知识包版本差异对比（P2-8）：对比两个版本规则编码与内容差异
     */
    @GetMapping("/packs/{packCode}/diff")
    public BaseResponse<PackDiff> diffPack(
            @PathVariable String packCode,
            @RequestParam(value = "from") String fromVersion,
            @RequestParam(value = "to") String toVersion) {
        return BaseResponse.ok(rulePackProvider.diff(packCode, fromVersion, toVersion));
    }

    /**
     * 发布规则集到市场
     */
    @Idempotent(key = "ruleAdmin:publishPack", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/packs")
    public BaseResponse<RulePack> publishPack(
            @Valid @RequestBody RulePack pack,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.ok(rulePackProvider.publish(pack, operator));
    }

    /**
     * 安装规则集（一键导入）
     */
    @PostMapping("/packs/{packCode}/install")
    public BaseResponse<InstallResult> installPack(
            @PathVariable String packCode,
            @RequestParam(value = "version", required = false) String version,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.ok(rulePackProvider.install(packCode, version, operator));
    }

    /**
     * 删除规则集
     */
    @OperationLog(module = "规则引擎", action = "删除规则集", bizType = "RULE_PACK")
    @Idempotent(key = "ruleAdmin:deletePack", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/packs/{id}")
    public BaseResponse<Void> deletePack(@PathVariable String id) {
        rulePackProvider.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 标记为官方
     */
    @Idempotent(key = "ruleAdmin:markOfficialPack", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/packs/{id}/official")
    public BaseResponse<Void> markOfficialPack(
            @PathVariable String id,
            @RequestParam(value = "official", defaultValue = "true") boolean official) {
        rulePackProvider.markOfficial(id, official);
        return BaseResponse.ok();
    }

    /**
     * 评分（0-5）
     */
    @Idempotent(key = "ruleAdmin:ratePack", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/packs/{id}/rate")
    public BaseResponse<Void> ratePack(
            @PathVariable String id,
            @RequestParam(value = "rating") double rating) {
        rulePackProvider.rate(id, rating);
        return BaseResponse.ok();
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
    @PostMapping("/stressTest")
    @Operation(summary = "规则压测", description = "使用线程池并发执行 Dry-run，统计 QPS、P50/P95/P99 耗时、错误率")
    public BaseResponse<RuleStressTestService.StressTestResult> stressTest(
            @RequestBody Map<String, Object> request) {
        RuleStressTestService svc = ruleStressTestServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.fail("规则压测服务未启用");
        }
        String ruleCode = (String) request.get("ruleCode");
        if (ruleCode != null && ruleCode.isBlank()) ruleCode = null;
        List<Map<String, Object>> factsList = new ArrayList<>();
        Object rawList = request.get("factsList");
        if (rawList instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> facts = new HashMap<>();
                    rawMap.forEach((k, v) -> facts.put(String.valueOf(k), v));
                    factsList.add(facts);
                }
            }
        }
        int threads = toInt(request.get("threads"), 10);
        int iterations = toInt(request.get("iterations"), 1000);
        int warmupIterations = toInt(request.get("warmupIterations"), 100);
        if (factsList == null || factsList.isEmpty()) {
            return BaseResponse.fail("factsList 不能为空");
        }
        return BaseResponse.ok(svc.run(ruleCode, factsList, threads, iterations, warmupIterations));
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
    @GetMapping("/packs/updateCheck")
    @Operation(summary = "知识包更新检查", description = "对比已安装知识包与市场最新版本，返回有更新的包列表")
    public BaseResponse<List<PackUpdateInfo>> checkPackUpdates() {
        return BaseResponse.ok(rulePackProvider.checkPackUpdates());
    }

    /**
     * 批量更新知识包到最新版本
     *
     * @param operator 操作人
     * @return 每个包的更新结果
     */
    @PostMapping("/packs/batchUpdate")
    @OperationLog(module = "规则引擎", action = "批量更新知识包", bizType = "RULE_PACK")
    @Operation(summary = "批量更新知识包", description = "将指定知识包列表更新到最新版本")
    public BaseResponse<List<InstallResult>> batchUpdatePacks(
            @RequestBody List<String> packCodes,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        if (packCodes == null || packCodes.isEmpty()) {
            return BaseResponse.ok(List.of());
        }
        List<InstallResult> results = new ArrayList<>();
        for (String packCode : packCodes) {
            try {
                results.add(rulePackProvider.install(packCode, null, operator));
            } catch (Exception e) {
                log.warn("[RuleAdmin] 批量更新知识包失败: packCode={}, err={}", packCode, e.getMessage());
            }
        }
        return BaseResponse.ok(results);
    }
}
