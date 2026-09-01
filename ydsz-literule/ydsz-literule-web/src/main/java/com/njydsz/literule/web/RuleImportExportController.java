package com.njydsz.literule.web;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.dto.RuleDefinition;
import com.njydsz.literule.api.dto.RuleImportDTO;
import com.njydsz.literule.server.config.RuleAdminService;

/**
 * 规则导入导出 Controller
 *
 * <p>业务背景：规则配置需要支持跨环境迁移（dev → sit → prod）和 GitOps 工作流。 通过 JSON 导入导出实现环境间同步，通过 YAML 导出适配 GitOps
 * 规则即代码场景。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>导出全部规则为 JSON（含核心配置字段）
 *   <li>导出全部规则为 YAML（GitOps 友好，可提交到 Git 仓库）
 *   <li>导入规则 JSON（自动重置版本和状态为 DRAFT）
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则导入导出", description = "规则 JSON/YAML 导入导出与 GitOps 集成")
public class RuleImportExportController {

  /** 规则管理服务 */
  private final RuleAdminService ruleAdminService;

  /** 导出全部规则为 JSON
   * @return 返回值说明
   */
  @GetMapping("/export")
  public YdszResponse<Map<String, Object>> exportRules() {
    List<RuleDefinition> rules = ruleAdminService.listAll();
    // 过滤掉内部字段，只导出核心配置
    List<Map<String, Object>> exportData =
        rules.stream()
            .map(
                r -> {
                  Map<String, Object> map = new LinkedHashMap<>();
                  map.put("code", r.getCode());
                  map.put("name", r.getName());
                  map.put("category", r.getCategory());
                  map.put("description", r.getDescription());
                  map.put("conditionExpression", r.getConditionExpression());
                  map.put("severityExpression", r.getSeverityExpression());
                  map.put(
                      "defaultSeverity",
                      r.getDefaultSeverity() != null ? r.getDefaultSeverity().name() : null);
                  map.put("titleTemplate", r.getTitleTemplate());
                  map.put("descriptionTemplate", r.getDescriptionTemplate());
                  map.put("priority", r.getPriority());
                  map.put("scope", r.getScope());
                  map.put("drilldownAvailable", r.isDrilldownAvailable());
                  map.put("status", r.getStatus());
                  map.put("version", r.getVersion());
                  return map;
                })
            .collect(Collectors.toList());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("exportTime", LocalDateTime.now().toString());
    result.put("ruleCount", rules.size());
    result.put("rules", exportData);
    return YdszResponse.success(result);
  }

  /**
   * 导出全部规则为 YAML（P2-11 GitOps）
   *
   * <p>供 GitOps 工作流使用：CI 定时拉取 YAML → 提交到 Git 仓库 → 审核合并后通过 Webhook 触发 /import 同步回 DB，实现规则即代码。
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
      sb.append("    conditionExpression: ")
          .append(escapeYaml(r.getConditionExpression()))
          .append("\n");
      if (r.getSeverityExpression() != null) {
        sb.append("    severityExpression: ")
            .append(escapeYaml(r.getSeverityExpression()))
            .append("\n");
      }
      sb.append("    defaultSeverity: ")
          .append(r.getDefaultSeverity() != null ? r.getDefaultSeverity().name() : "YELLOW")
          .append("\n");
      if (r.getTitleTemplate() != null) {
        sb.append("    titleTemplate: ").append(escapeYaml(r.getTitleTemplate())).append("\n");
      }
      if (r.getDescriptionTemplate() != null) {
        sb.append("    descriptionTemplate: ")
            .append(escapeYaml(r.getDescriptionTemplate()))
            .append("\n");
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

  /** YAML 字符串转义（处理特殊字符与换行） */
  private String escapeYaml(String s) {
    if (s == null) {
      return "null";
    }
    // 含特殊字符时用双引号包裹并转义
    if (s.contains(":") || s.contains("#") || s.contains("\n") || s.contains("\"")) {
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
    return s;
  }

  /** 导入规则（JSON 格式）
   * @param operator 参数说明
      * @return 返回值说明
      * @param dto 参数说明
   */
  @Idempotent(key = "ruleAdmin:importRules", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @RateLimit(resource = "literule.rule_import_export.importRules", threshold = 50)
  @PostMapping("/import")
  public YdszResponse<Map<String, Object>> importRules(
      @Valid @RequestBody RuleImportDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    List<Map<String, Object>> rules = dto.getRules();
    if (rules == null || rules.isEmpty()) {
      return YdszResponse.success(Map.of("imported", 0, "skipped", 0));
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
        RuleDefinition def = YdszJson.convertValue(ruleMap, RuleDefinition.class);
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
    return YdszResponse.success(result);
  }
}
