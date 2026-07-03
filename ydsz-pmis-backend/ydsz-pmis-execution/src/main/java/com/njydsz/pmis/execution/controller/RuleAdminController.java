package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.entity.DecisionTableDO;
import com.njydsz.pmis.execution.entity.RuleExecutionTraceDO;
import com.njydsz.pmis.execution.entity.RuleTemplateDO;
import com.njydsz.pmis.execution.entity.RuleTestCaseDO;
import com.njydsz.pmis.execution.literule.RuleConflictDetector;
import com.njydsz.pmis.execution.literule.RuleGenerationService;
import com.njydsz.pmis.execution.literule.RuleTemplateService;
import com.njydsz.pmis.execution.mapper.DecisionTableMapper;
import com.njydsz.pmis.execution.mapper.RuleTestCaseMapper;
import com.njydsz.pmis.execution.mapper.RuleExecutionTraceMapper;
import com.njydsz.pmis.execution.service.DecisionTableEvalService;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.config.ABTestService;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
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
                                                      @RequestBody Map<String, Object> request) {
        RuleDefinition currentDef = ruleAdminService.getByCode(ruleCode);
        if (currentDef == null) {
            return Result.fail("规则不存在: " + ruleCode);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> candidateMap = (Map<String, Object>) request.get("candidate");
        @SuppressWarnings("unchecked")
        Map<String, Object> facts = (Map<String, Object>) request.get("facts");

        if (candidateMap == null || facts == null) {
            return Result.fail("请求体必须包含 candidate 和 facts 字段");
        }

        // 构建候选规则定义（基于当前规则，覆盖候选字段）
        RuleDefinition candidateDef = objectMapper.convertValue(candidateMap, RuleDefinition.class);
        candidateDef.setCode(ruleCode);

        return Result.ok(abTestService.test(currentDef, candidateDef, facts));
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
    public Result<RuleDefinition> aiGenerate(@RequestBody Map<String, Object> request) {
        String description = (String) request.get("description");
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) request.get("availableFields");
        if (fields == null) fields = List.of();
        return Result.ok(ruleGenerationService.generate(description, fields));
    }

    /**
     * AI 辅助生成并保存规则定义
     *
     * @param request  请求体，包含 description（自然语言描述）和 availableFields（可用字段列表）
     * @param operator 操作人（从 Header 获取）
     * @return 保存后的规则定义
     */
    @PostMapping("/ai-generate-and-save")
    public Result<RuleDefinition> aiGenerateAndSave(@RequestBody Map<String, Object> request,
                                                      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        String description = (String) request.get("description");
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) request.get("availableFields");
        if (fields == null) fields = List.of();
        return Result.ok(ruleGenerationService.generateAndSave(description, fields, operator));
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
    @DeleteMapping("/test-cases/{id}")
    public Result<Void> deleteTestCase(@PathVariable Long id) {
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
    public Result<Map<String, Object>> batchRunTestCases(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) request.get("ids");

        List<RuleTestCaseDO> testCases;
        if (ids == null || ids.isEmpty()) {
            // 执行全部测试用例
            testCases = ruleTestCaseMapper.selectList(null);
        } else {
            testCases = ids.stream()
                .map(id -> ruleTestCaseMapper.selectById(id.longValue()))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        }

        if (testCases.isEmpty()) {
            return Result.ok(Map.of("total", 0, "passed", 0, "failed", 0, "passRate", "100%"));
        }

        List<Map<String, Object>> caseResults = new java.util.ArrayList<>();
        int passed = 0;
        int failed = 0;

        for (RuleTestCaseDO tc : testCases) {
            List<RuleResult> results = ruleAdminService.dryRun(null, tc.getFactsData());

            // 获取实际触发的规则编码集合
            java.util.Set<String> actualTriggered = results.stream()
                .map(RuleResult::getRuleCode)
                .collect(java.util.stream.Collectors.toSet());

            // 获取预期触发的规则编码集合
            java.util.Set<String> expectedTriggered = new java.util.HashSet<>();
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

            java.util.Set<String> missing = new java.util.LinkedHashSet<>(expectedTriggered);
            missing.removeAll(actualTriggered);

            java.util.Set<String> unexpected = new java.util.LinkedHashSet<>(actualTriggered);
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
    public Result<RuleDefinition> changeStatus(@PathVariable String ruleCode,
                                               @RequestBody Map<String, String> request,
                                               @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        String targetStatus = request.get("targetStatus");
        String comment = request.getOrDefault("comment", "");
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
                                                               @RequestParam(defaultValue = "20") int limit) {
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
        java.util.Set<String> historicalTriggered = traces.stream()
            .filter(t -> Boolean.TRUE.equals(t.getTriggered()))
            .map(RuleExecutionTraceDO::getRuleCode)
            .collect(java.util.stream.Collectors.toSet());

        // 构建当前触发规则编码集合
        java.util.Set<String> currentTriggered = currentResults.stream()
            .map(RuleResult::getRuleCode)
            .collect(java.util.stream.Collectors.toSet());

        // 差异分析
        java.util.Set<String> added = new java.util.LinkedHashSet<>(currentTriggered);
        added.removeAll(historicalTriggered);

        java.util.Set<String> removed = new java.util.LinkedHashSet<>(historicalTriggered);
        removed.removeAll(currentTriggered);

        java.util.Set<String> unchanged = new java.util.LinkedHashSet<>(currentTriggered);
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
     * 查询最近执行链路（按时间倒序）
     *
     * @param limit 返回条数（默认 50）
     * @return 最近的执行链路列表
     */
    @GetMapping("/traces")
    public Result<List<RuleExecutionTraceDO>> listRecentTraces(@RequestParam(defaultValue = "50") int limit) {
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
    @DeleteMapping("/decision-tables/{id}")
    public Result<Void> deleteDecisionTable(@PathVariable Long id) {
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
     * 导入规则（JSON 格式）
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importRules(@RequestBody Map<String, Object> request,
                                                    @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) request.get("rules");
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
}
