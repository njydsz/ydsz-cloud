package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.entity.RuleTemplateDO;
import com.njydsz.pmis.execution.literule.RuleGenerationService;
import com.njydsz.pmis.execution.literule.RuleTemplateService;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.spi.RuleVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    private final RuleEngine ruleEngine;
    private final RuleTemplateService ruleTemplateService;
    private final RuleGenerationService ruleGenerationService;

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
}
