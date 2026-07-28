package com.njydsz.literule.web.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.dsl.RuleDsl;
import com.njydsz.literule.server.dsl.RuleDslConverter;
import com.njydsz.literule.server.dsl.RuleDslEntry;
import com.njydsz.literule.server.dsl.RuleDslExporter;
import com.njydsz.literule.server.dsl.RuleDslParser;
import com.njydsz.literule.api.expr.ExpressionEvaluator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.literule.domain.vo.RuleDslVO;

/**
 * 规则 DSL 管理接口（P3-6 DSL 语言支持）
 *
 * <p>提供 DSL 的校验、解析、导入和导出能力，支持 YAML 和 JSON 格式。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@code POST /ruleEngine/dsl/validate} - 校验 DSL 内容（YAML/JSON）</li>
 *   <li>{@code POST /ruleEngine/dsl/parse} - 解析 DSL 为结构化模型</li>
 *   <li>{@code POST /ruleEngine/dsl/import} - 导入 DSL 规则到引擎</li>
 *   <li>{@code GET  /ruleEngine/dsl/export} - 导出全部规则为 YAML DSL</li>
 *   <li>{@code GET  /ruleEngine/dsl/export/{ruleCode}} - 导出单条规则为 YAML DSL</li>
 *   <li>{@code POST /ruleEngine/dsl/preview} - 预览 DSL 规则的评估结果</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/dsl")
@RequiredArgsConstructor
@Tag(name = "规则DSL管理", description = "DSL 校验/解析/导入/导出")
public class RuleDslController {

    private final RuleAdminService ruleAdminService;
    private final ExpressionEvaluator evaluator;

    /**
     * 校验 DSL 内容
     *
     * <p>解析 DSL 文本并校验：
     * <ul>
     *   <li>YAML/JSON 格式合法性</li>
     *   <li>必填字段完整性（code/name/condition 等）</li>
     *   <li>表达式语法合法性</li>
     *   <li>链引用规则是否存在</li>
     * </ul>
     *
     * @param request 请求体（含 content 和 format 字段）
     * @return 校验结果（valid + errors + ruleCount）
     */
    @Audit(module = "DSL管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/validate")
    @Operation(summary = "校验DSL", description = "校验 YAML/JSON 格式的 DSL 内容合法性")
    public BaseResponse<Map<String, Object>> validate(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        String format = (String) request.getOrDefault("format", "yaml");

        if (content == null || content.isBlank()) {
            return BaseResponse.error("DSL 内容不能为空");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            RuleDsl dsl = "json".equalsIgnoreCase(format)
                    ? RuleDslParser.parseJson(content)
                    : RuleDslParser.parseYaml(content);

            // 校验 DSL 结构
            RuleDslParser.validate(dsl);

            // 校验表达式语法
            List<String> errors = new ArrayList<>();
            int ruleCount = 0;
            if (dsl.getRules() != null) {
                for (RuleDslEntry entry : dsl.getRules()) {
                    ruleCount++;
                    String type = entry.getType() == null ? "expression" : entry.getType().toLowerCase();
                    if ("expression".equals(type) && entry.getCondition() != null) {
                        if (!evaluator.validate(entry.getCondition())) {
                            errors.add("规则 " + entry.getCode() + " 的条件表达式语法错误: " + entry.getCondition());
                        }
                    }
                    if (entry.getSeverityExpression() != null && !entry.getSeverityExpression().isBlank()) {
                        if (!evaluator.validate(entry.getSeverityExpression())) {
                            errors.add("规则 " + entry.getCode() + " 的严重度表达式语法错误: " + entry.getSeverityExpression());
                        }
                    }
                }
            }

            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            result.put("ruleCount", ruleCount);
            result.put("chainCount", dsl.getChains() != null ? dsl.getChains().size() : 0);
            return BaseResponse.success(result);

        } catch (IllegalArgumentException e) {
            result.put("valid", false);
            result.put("errors", List.of(e.getMessage()));
            result.put("ruleCount", 0);
            return BaseResponse.success(result);
        } catch (Exception e) {
            log.warn("[DSL] 校验失败: {}", e.getMessage());
            return BaseResponse.error("DSL 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 DSL 为结构化模型
     *
     * @param request 请求体（含 content 和 format）
     * @return DSL 模型（rules + chains + meta）
     */
    @Audit(module = "DSL管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'parse'")
    @PostMapping("/parse")
    @Operation(summary = "解析DSL", description = "将 YAML/JSON DSL 文本解析为结构化模型")
    public BaseResponse<RuleDslVO> parse(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        String format = (String) request.getOrDefault("format", "yaml");

        if (content == null || content.isBlank()) {
            return BaseResponse.error("DSL 内容不能为空");
        }

        try {
            RuleDsl dsl = "json".equalsIgnoreCase(format)
                    ? RuleDslParser.parseJson(content)
                    : RuleDslParser.parseYaml(content);
            return BaseResponse.success(dsl);
        } catch (Exception e) {
            log.warn("[DSL] 解析失败: {}", e.getMessage());
            return BaseResponse.error("DSL 解析失败: " + e.getMessage());
        }
    }

    /**
     * 导入 DSL 规则到引擎
     *
     * <p>解析 DSL 文本，将规则定义导入到引擎中（调用 {@link RuleAdminService#save} 逐条保存）。
     * 导入行为为"upsert"：已存在的规则覆盖更新，不存在的规则创建。
     *
     * @param request  请求体（含 content / format / operator）
     * @return 导入结果（成功/失败计数 + 详细信息）
     */
    @Audit(module = "DSL管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/import")
    @Operation(summary = "导入DSL规则", description = "将 YAML/JSON DSL 导入到规则引擎")
    public BaseResponse<Map<String, Object>> importDsl(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        String format = (String) request.getOrDefault("format", "yaml");
        String operator = (String) request.getOrDefault("operator", "SYSTEM");

        if (content == null || content.isBlank()) {
            return BaseResponse.error("DSL 内容不能为空");
        }

        try {
            RuleDsl dsl = "json".equalsIgnoreCase(format)
                    ? RuleDslParser.parseJson(content)
                    : RuleDslParser.parseYaml(content);

            RuleDslParser.validate(dsl);

            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            List<String> importedCodes = new ArrayList<>();

            if (dsl.getRules() != null) {
                for (RuleDslEntry entry : dsl.getRules()) {
                    try {
                        RuleDefinition def = RuleDslConverter.toRuleDefinition(entry);
                        ruleAdminService.save(def, operator, "DSL 导入");
                        successCount++;
                        importedCodes.add(entry.getCode());
                    } catch (Exception e) {
                        failCount++;
                        errors.add("规则 " + entry.getCode() + " 导入失败: " + e.getMessage());
                        log.warn("[DSL] 规则 {} 导入失败: {}", entry.getCode(), e.getMessage());
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalRules", dsl.getRules() != null ? dsl.getRules().size() : 0);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("importedCodes", importedCodes);
            result.put("errors", errors);
            result.put("summary", String.format("共 %d 条，成功 %d 条，失败 %d 条",
                    dsl.getRules() != null ? dsl.getRules().size() : 0, successCount, failCount));

            log.info("[DSL] 导入完成: success={}, fail={}", successCount, failCount);
            return BaseResponse.success(result);

        } catch (Exception e) {
            log.warn("[DSL] 导入失败: {}", e.getMessage());
            return BaseResponse.error("DSL 导入失败: " + e.getMessage());
        }
    }

    /**
     * 导出全部规则为 YAML DSL
     *
     * @param category 分类过滤（可选，为空导出全部）
     * @return YAML 格式的 DSL 文本
     */
    @GetMapping("/export")
    @Operation(summary = "导出全部规则DSL", description = "将引擎中的规则导出为 YAML 格式的 DSL")
    public BaseResponse<Map<String, Object>> exportAll(
            @RequestParam(value = "category", required = false) String category) {
        List<RuleDefinition> allRules = ruleAdminService.listAll();

        // 分类过滤
        if (category != null && !category.isBlank()) {
            allRules = allRules.stream()
                    .filter(r -> category.equalsIgnoreCase(r.getCategory()))
                    .toList();
        }

        if (allRules.isEmpty()) {
            return BaseResponse.error("没有可导出的规则");
        }

        String yaml = RuleDslExporter.exportYaml(allRules, "exported-rules",
                "导出时间: " + LocalDateTime.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "yaml");
        result.put("ruleCount", allRules.size());
        result.put("content", yaml);
        return BaseResponse.success(result);
    }

    /**
     * 导出单条规则为 YAML DSL
     *
     * @param ruleCode 规则编码
     * @return YAML 格式的 DSL 文本
     */
    @GetMapping("/export/{ruleCode}")
    @Operation(summary = "导出单条规则DSL", description = "将指定规则导出为 YAML 格式的 DSL")
    public BaseResponse<Map<String, Object>> exportSingle(@PathVariable String ruleCode) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.error("规则不存在: " + ruleCode);
        }

        String yaml = RuleDslExporter.exportSingleRule(def);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "yaml");
        result.put("ruleCode", ruleCode);
        result.put("content", yaml);
        return BaseResponse.success(result);
    }

    /**
     * 预览 DSL 规则的评估结果
     *
     * <p>解析 DSL 文本，构建临时规则实例，对提供的事实数据进行 dry-run 评估。
     * 不持久化、不注册到引擎。
     *
     * @param request 请求体（含 content / format / facts）
     * @return 评估结果列表
     */
    @Audit(module = "DSL管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/preview")
    @Operation(summary = "预览DSL评估", description = "解析 DSL 并用提供的事实数据试运行，不持久化")
    public BaseResponse<List<Map<String, Object>>> preview(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        String format = (String) request.getOrDefault("format", "yaml");

        if (content == null || content.isBlank()) {
            return BaseResponse.error("DSL 内容不能为空");
        }

        Object factsObj = request.get("facts");
        Map<String, Object> facts = new LinkedHashMap<>();
        if (factsObj instanceof Map<?, ?> fm) {
            for (Map.Entry<?, ?> e : fm.entrySet()) {
                if (e.getKey() != null) {
                    facts.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }

        try {
            RuleDsl dsl = "json".equalsIgnoreCase(format)
                    ? RuleDslParser.parseJson(content)
                    : RuleDslParser.parseYaml(content);

            RuleDslParser.validate(dsl);

            List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
            RuleContext context =
                    RuleContext.of(facts, "DSL_PREVIEW", "MANUAL");

            List<Map<String, Object>> results = new ArrayList<>();
            for (Rule rule : rules) {
                try {
                    RuleResult result = rule.evaluate(context);
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("ruleCode", result.getRuleCode());
                    r.put("triggered", result.isTriggered());
                    r.put("severity", result.getSeverity() != null ? result.getSeverity().name() : null);
                    r.put("title", result.getTitle());
                    r.put("description", result.getDescription());
                    results.add(r);
                } catch (Exception e) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("ruleCode", rule.getCode());
                    r.put("triggered", false);
                    r.put("error", e.getMessage());
                    results.add(r);
                }
            }

            return BaseResponse.success(results);

        } catch (Exception e) {
            log.warn("[DSL] 预览失败: {}", e.getMessage());
            return BaseResponse.error("DSL 预览失败: " + e.getMessage());
        }
    }
}
