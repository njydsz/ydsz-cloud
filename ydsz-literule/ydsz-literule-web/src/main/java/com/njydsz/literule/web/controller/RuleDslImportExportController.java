package com.njydsz.literule.web.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.dsl.RuleDsl;
import com.njydsz.literule.server.dsl.RuleDslConverter;
import com.njydsz.literule.server.dsl.RuleDslEntry;
import com.njydsz.literule.server.dsl.RuleDslExporter;
import com.njydsz.literule.server.dsl.RuleDslParser;

/**
 * 规则 DSL 导入导出 Controller（P3-6 DSL 语言支持）
 *
 * <p>提供 DSL 规则的导入和导出能力，支持跨环境迁移（dev → sit → prod）和 GitOps 工作流。
 *
 * <h3>接口列表</h3>
 *
 * <ul>
 *   <li>{@code POST /ruleEngine/dsl/import} - 导入 DSL 规则到引擎（upsert 语义）
 *   <li>{@code GET /ruleEngine/dsl/export} - 导出全部规则为 YAML DSL
 *   <li>{@code GET /ruleEngine/dsl/export/{ruleCode}} - 导出单条规则为 YAML DSL
 * </ul>
 *
 * <p><b>拆分说明：</b>本类从原 {@link RuleDslController} 拆分而来，仅保留导入导出相关接口。 DSL 校验 / 解析 / 预览见 {@link
 * RuleDslController}。
 *
 * <h3>导入语义</h3>
 *
 * <p>导入行为为 "upsert"：已存在的规则覆盖更新，不存在的规则创建。 每条规则独立 try-catch，单条失败不影响其他规则导入，失败详情通过 errors 字段返回。
 *
 * <h3>导出格式</h3>
 *
 * <p>当前仅支持 YAML 格式导出（GitOps 友好，可提交到 Git 仓库）。JSON 格式导出请使用 {@code RuleImportExportController} 的 JSON
 * 导出接口。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleDslController DSL 校验 / 解析 / 预览接口
 * @see RuleAdminService 规则管理服务
 * @see RuleDslExporter DSL 导出器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/dsl")
@RequiredArgsConstructor
@Tag(name = "规则DSL导入导出", description = "DSL 规则导入 / 导出")
public class RuleDslImportExportController {

  private final RuleAdminService ruleAdminService;

  /**
   * 导入 DSL 规则到引擎。
   *
   * <p>解析 DSL 文本，将规则定义导入到引擎中（调用 {@link RuleAdminService#save} 逐条保存）。 导入行为为
   * "upsert"：已存在的规则覆盖更新，不存在的规则创建。
   *
   * @param request 请求体（含 content / format / operator）
   * @return 导入结果（成功 / 失败计数 + 详细信息）
   */
  @Audit(
      module = "DSL管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'importDsl'")
  @RateLimit(resource = "literule.rule_dsl_import_export.importDsl", threshold = 50)
  @PostMapping("/import")
  @Operation(summary = "导入DSL规则", description = "将 YAML/JSON DSL 导入到规则引擎（upsert 语义，单条失败不影响整体）")
  public YdszResponse<Map<String, Object>> importDsl(@RequestBody Map<String, Object> request) {
    String content = (String) request.get("content");
    String format = (String) request.getOrDefault("format", "yaml");
    String operator = (String) request.getOrDefault("operator", "SYSTEM");

    if (content == null || content.isBlank()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "DSL 内容不能为空");
    }

    try {
      RuleDsl dsl =
          "json".equalsIgnoreCase(format)
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
            RuleDefinitionDTO def = RuleDslConverter.toRuleDefinition(entry);
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
      result.put(
          "summary",
          String.format(
              "共 %d 条，成功 %d 条，失败 %d 条",
              dsl.getRules() != null ? dsl.getRules().size() : 0, successCount, failCount));

      log.info("[DSL] 导入完成: success={}, fail={}", successCount, failCount);
      return YdszResponse.success(result);

    } catch (Exception e) {
      log.warn("[DSL] 导入失败: {}", e.getMessage());
      return YdszResponse.error(
          LiteruleExceptionCode.DSL_PARSE_ERROR, "DSL 导入失败: " + e.getMessage());
    }
  }

  /**
   * 导出全部规则为 YAML DSL。
   *
   * @param category 分类过滤（可选，为空导出全部）
   * @return YAML 格式的 DSL 文本
   */
  @GetMapping("/export")
  @Operation(summary = "导出全部规则DSL", description = "将引擎中的规则导出为 YAML 格式的 DSL")
  public YdszResponse<Map<String, Object>> exportAll(
      @RequestParam(value = "category", required = false) String category) {
    List<RuleDefinitionDTO> allRules = ruleAdminService.listAll();

    // 分类过滤
    if (category != null && !category.isBlank()) {
      allRules = allRules.stream().filter(r -> category.equalsIgnoreCase(r.getCategory())).toList();
    }

    if (allRules.isEmpty()) {
      return YdszResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "没有可导出的规则");
    }

    String yaml =
        RuleDslExporter.exportYaml(allRules, "exported-rules", "导出时间: " + LocalDateTime.now());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("format", "yaml");
    result.put("ruleCount", allRules.size());
    result.put("content", yaml);
    return YdszResponse.success(result);
  }

  /**
   * 导出单条规则为 YAML DSL。
   *
   * @param ruleCode 规则编码
   * @return YAML 格式的 DSL 文本
   */
  @GetMapping("/export/{ruleCode}")
  @Operation(summary = "导出单条规则DSL", description = "将指定规则导出为 YAML 格式的 DSL")
  public YdszResponse<Map<String, Object>> exportSingle(@PathVariable String ruleCode) {
    RuleDefinitionDTO def = ruleAdminService.getByCode(ruleCode);
    if (def == null) {
      return YdszResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
    }

    String yaml = RuleDslExporter.exportSingleRule(def);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("format", "yaml");
    result.put("ruleCode", ruleCode);
    result.put("content", yaml);
    return YdszResponse.success(result);
  }
}

