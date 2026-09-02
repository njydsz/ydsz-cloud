package com.njydsz.literule.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleDslVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.dsl.RuleDsl;
import com.njydsz.literule.server.dsl.RuleDslConverter;
import com.njydsz.literule.server.dsl.RuleDslEntry;
import com.njydsz.literule.server.dsl.RuleDslParser;

/**
 * 规则 DSL 校验 / 解析 / 预览 Controller（P3-6 DSL 语言支持）
 *
 * <p>提供 DSL 的校验、解析和预览评估能力，支持 YAML 和 JSON 格式。所有接口均为只读 （不修改引擎状态），适合在可视化编辑器中实时反馈 DSL 编辑结果。
 *
 * <h3>接口列表</h3>
 *
 * <ul>
 *   <li>{@code POST /ruleEngine/dsl/validate} - 校验 DSL 内容（YAML / JSON）
 *   <li>{@code POST /ruleEngine/dsl/parse} - 解析 DSL 为结构化模型
 *   <li>{@code POST /ruleEngine/dsl/preview} - 预览 DSL 规则的评估结果（dry-run）
 * </ul>
 *
 * <p><b>拆分说明：</b>本类从原 {@code RuleDslController} 拆分而来，仅保留校验 / 解析 / 预览接口。 DSL 导入 / 导出见 {@link
 * RuleDslImportExportController}。
 *
 * <h3>校验维度</h3>
 *
 * <ul>
 *   <li>YAML / JSON 格式合法性
 *   <li>必填字段完整性（code / name / condition 等）
 *   <li>表达式语法合法性（通过 {@link ExpressionEngine#validate}）
 *   <li>链引用规则是否存在
 * </ul>
 *
 * <h3>预览语义</h3>
 *
 * <p>预览接口解析 DSL 文本，构建临时规则实例，对提供的事实数据进行 dry-run 评估。 不持久化、不注册到引擎，适合在编辑器中实时查看规则触发情况。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleDslImportExportController DSL 导入 / 导出接口
 * @see ExpressionEngine 表达式校验器
 * @see RuleDslParser DSL 解析器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/dsl")
@RequiredArgsConstructor
@Tag(name = "规则DSL校验解析", description = "DSL 校验 / 解析 / 预览")
public class RuleDslController {

  private final ExpressionEngine evaluator;

  /**
   * 校验 DSL 内容。
   *
   * <p>解析 DSL 文本并校验：
   *
   * <ul>
   *   <li>YAML / JSON 格式合法性
   *   <li>必填字段完整性（code / name / condition 等）
   *   <li>表达式语法合法性
   *   <li>链引用规则是否存在
   * </ul>
   *
   * @param request 请求体（含 content 和 format 字段）
   * @return 校验结果（valid + errors + ruleCount）
   */
  @Audit(
      module = "DSL管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'validate'")
  @RateLimit(resource = "literule.rule_dsl.validate", threshold = 50)
  @PostMapping("/validate")
  @Operation(summary = "校验DSL", description = "校验 YAML/JSON 格式的 DSL 内容合法性")
  public YdszResponse<Map<String, Object>> validate(@RequestBody Map<String, Object> request) {
    String content = (String) request.get("content");
    String format = (String) request.getOrDefault("format", "yaml");

    if (content == null || content.isBlank()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "DSL 内容不能为空");
    }

    Map<String, Object> result = new LinkedHashMap<>(16);
    try {
      RuleDsl dsl =
          "json".equalsIgnoreCase(format)
              ? RuleDslParser.parseJson(content)
              : RuleDslParser.parseYaml(content);

      // 校验 DSL 结构
      RuleDslParser.validate(dsl);

      // 校验表达式语法
      List<String> errors = new ArrayList<>(16);
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
              errors.add(
                  "规则 " + entry.getCode() + " 的严重度表达式语法错误: " + entry.getSeverityExpression());
            }
          }
        }
      }

      result.put("valid", errors.isEmpty());
      result.put("errors", errors);
      result.put("ruleCount", ruleCount);
      result.put("chainCount", dsl.getChains() != null ? dsl.getChains().size() : 0);
      return YdszResponse.success(result);

    } catch (IllegalArgumentException e) {
      result.put("valid", false);
      result.put("errors", List.of(e.getMessage()));
      result.put("ruleCount", 0);
      return YdszResponse.success(result);
    } catch (Exception e) {
      log.warn("[DSL] 校验失败: {}", e.getMessage());
      return YdszResponse.error(
          LiteruleExceptionCode.DSL_PARSE_ERROR, "DSL 解析失败: " + e.getMessage());
    }
  }

  /**
   * 解析 DSL 为结构化模型。
   *
   * @param request 请求体（含 content 和 format）
   * @return DSL 模型（rules + chains + meta）
   */
  @Audit(
      module = "DSL管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'parse'")
  @RateLimit(resource = "literule.rule_dsl.parse", threshold = 50)
  @PostMapping("/parse")
  @Operation(summary = "解析DSL", description = "将 YAML/JSON DSL 文本解析为结构化模型")
  public YdszResponse<RuleDslVO> parse(@RequestBody Map<String, Object> request) {
    String content = (String) request.get("content");
    String format = (String) request.getOrDefault("format", "yaml");

    if (content == null || content.isBlank()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "DSL 内容不能为空");
    }

    try {
      RuleDsl dsl =
          "json".equalsIgnoreCase(format)
              ? RuleDslParser.parseJson(content)
              : RuleDslParser.parseYaml(content);
      return YdszResponse.success(toDslVO(dsl));
    } catch (Exception e) {
      log.warn("[DSL] 解析失败: {}", e.getMessage());
      return YdszResponse.error(
          LiteruleExceptionCode.DSL_PARSE_ERROR, "DSL 解析失败: " + e.getMessage());
    }
  }

  /**
   * 预览 DSL 规则的评估结果。
   *
   * <p>解析 DSL 文本，构建临时规则实例，对提供的事实数据进行 dry-run 评估。 不持久化、不注册到引擎。
   *
   * @param request 请求体（含 content / format / facts）
   * @return 评估结果列表
   */
  @Audit(
      module = "DSL管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'preview'")
  @RateLimit(resource = "literule.rule_dsl.preview", threshold = 50)
  @PostMapping("/preview")
  @Operation(summary = "预览DSL评估", description = "解析 DSL 并用提供的事实数据试运行，不持久化")
  public YdszResponse<List<Map<String, Object>>> preview(@RequestBody Map<String, Object> request) {
    String content = (String) request.get("content");
    String format = (String) request.getOrDefault("format", "yaml");

    if (content == null || content.isBlank()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "DSL 内容不能为空");
    }

    Object factsObj = request.get("facts");
    Map<String, Object> facts = new LinkedHashMap<>(16);
    if (factsObj instanceof Map<?, ?> fm) {
      for (Map.Entry<?, ?> e : fm.entrySet()) {
        if (e.getKey() != null) {
          facts.put(String.valueOf(e.getKey()), e.getValue());
        }
      }
    }

    try {
      RuleDsl dsl =
          "json".equalsIgnoreCase(format)
              ? RuleDslParser.parseJson(content)
              : RuleDslParser.parseYaml(content);

      RuleDslParser.validate(dsl);

      List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);
      RuleContextVO context = RuleContextVO.of(facts, "DSL_PREVIEW", "MANUAL");

      List<Map<String, Object>> results = new ArrayList<>(16);
      for (Rule rule : rules) {
        try {
          RuleResultVO result = rule.evaluate(context);
          Map<String, Object> r = new LinkedHashMap<>(16);
          r.put("ruleCode", result.getRuleCode());
          r.put("triggered", result.isTriggered());
          r.put("severity", result.getSeverity() != null ? RuleSeverity.fromCode(result.getSeverity()).name() : null);
          r.put("title", result.getTitle());
          r.put("description", result.getDescription());
          results.add(r);
        } catch (Exception e) {
          Map<String, Object> r = new LinkedHashMap<>(16);
          r.put("ruleCode", rule.getCode());
          r.put("triggered", false);
          r.put("error", e.getMessage());
          results.add(r);
        }
      }

      return YdszResponse.success(results);

    } catch (Exception e) {
      log.warn("[DSL] 预览失败: {}", e.getMessage());
      return YdszResponse.error(
          LiteruleExceptionCode.DSL_PARSE_ERROR, "DSL 预览失败: " + e.getMessage());
    }
  }

  /** RuleDsl → RuleDslVO 转换 */
  private RuleDslVO toDslVO(RuleDsl dsl) {
    RuleDslVO vo = new RuleDslVO();
    vo.setRules(
        dsl.getRules() == null ? null : dsl.getRules().stream().map(e -> (Object) e).toList());
    vo.setChains(
        dsl.getChains() == null ? null : dsl.getChains().stream().map(e -> (Object) e).toList());
    vo.setMeta(dsl.getMeta());
    return vo;
  }
}

