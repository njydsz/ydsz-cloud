package com.njydsz.literule.server.config;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.server.spi.RuleConfigProvider;

/**
 * 规则冲突检测器
 *
 * <p>在规则保存前检测新规则与现有规则的潜在冲突，输出 {@link RuleConflict} 列表。
 *
 * <p>检测维度（1.5.0 增强表达式归一化与范围重叠分析）：
 *
 * <ul>
 *   <li>{@link RuleConflict.Type#IDENTICAL_CONDITION}：同 category + 同 tenantId 下，
 *       条件表达式归一化后完全相同（WARN，可能重复定义）
 *   <li>{@link RuleConflict.Type#CONTRADICTORY_SEVERITY}：条件表达式相同但严重度不同 （ERROR，语义冲突）
 *   <li>{@link RuleConflict.Type#NAME_COLLISION}：同 category + 同 tenantId 下， name
 *       相同但条件表达式不同（WARN，命名冲突）
 *   <li>{@link RuleConflict.Type#CONDITION_OVERLAP}：条件范围重叠（WARN），
 *       两条规则在同一变量上存在范围交集，可能导致同一事实同时命中（1.5.0 起）
 * </ul>
 *
 * <p><b>表达式归一化（1.5.0 增强）</b>：
 *
 * <ul>
 *   <li>去除所有空白字符
 *   <li>统一逻辑运算符：{@code and}→{@code &&}、{@code or}→{@code ||}、{@code not}→{@code !}
 *   <li>统一大小写
 *   <li>翻转比较操作数顺序：{@code 3 &lt; x} → {@code x &gt; 3}（规范化为 变量在左、常量在右）
 * </ul>
 *
 * <p><b>条件重叠分析（1.5.0 新增）</b>： 仅对简单比较表达式（{@code var OP number}）做范围交集检测， 复杂表达式（含 &amp;&amp; / ||
 * 或嵌套）降级为不检测，避免误报。 同互斥组内的规则不报重叠（互斥组本身保证短路）。
 *
 * <p>租户隔离：仅在同一 tenantId 内检测冲突（单租户部署下 tenantId 恒为 1）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@RequiredArgsConstructor
public class RuleConflictDetector {

    /** 比较表达式正则捕获组：右侧操作数 */
  private static final int CMP_GROUP_RIGHT = 3;

  /** 规则配置提供者（SPI），用于加载同租户同分类下的现有规则以检测冲突 */
  private final RuleConfigProvider configProvider;

  /** 简单比较表达式模式：var OP number（用于范围重叠分析） */
  private static final Pattern COMPARISON_PATTERN =
      Pattern.compile("^([a-zA-Z_]\\w*)\\s*(>=|<=|>|<|==|!=)\\s*(-?\\d+(?:\\.\\d+)?)$");

  /** 反向比较表达式模式：number OP var（如 "3 < x"） */
  private static final Pattern REVERSE_COMPARISON_PATTERN =
      Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*(>=|<=|>|<|==|!=)\\s*([a-zA-Z_]\\w*)$");

  /**
   * 检测新规则与所有现有规则的冲突
   *
   * @param newDefinition 待保存的新规则定义
   * @return 冲突列表；无冲突返回空列表
   */
  public List<RuleConflict> detect(RuleDefinitionDTO newDefinition) {
    List<RuleConflict> conflicts = new ArrayList<>(16);
    if (newDefinition == null || newDefinition.getCode() == null) {
      return conflicts;
    }
    List<RuleDefinitionDTO> existingRules = configProvider.loadAllRules();
    String newExpr = normalizeExpression(newDefinition.getConditionExpression());
    String newMutex = newDefinition.getMutexGroup();
    for (RuleDefinitionDTO existing : existingRules) {
      if (existing == null || existing.getCode() == null) {
        continue;
      }
      if (existing.getCode().equals(newDefinition.getCode())) {
        continue; // skip self (update scenario)
      }
      if (newMutex != null && newMutex.equals(existing.getMutexGroup())) {
        continue; // mutex group rules don't conflict
      }
      String existingExpr = normalizeExpression(existing.getConditionExpression());
      // Identical condition
      if (newExpr != null && !newExpr.isEmpty() && newExpr.equals(existingExpr)) {
        if (newDefinition.getDefaultSeverity() != null
            && !newDefinition.getDefaultSeverity().equals(existing.getDefaultSeverity())) {
          conflicts.add(RuleConflict.builder()
              .type(RuleConflict.Type.CONTRADICTORY_SEVERITY)
              .level(RuleConflict.Level.ERROR)
              .newRuleCode(newDefinition.getCode())
              .conflictingRuleCode(existing.getCode())
              .description("条件表达式相同但严重度不同: " + existingExpr)
              .build());
        } else {
          conflicts.add(RuleConflict.builder()
              .type(RuleConflict.Type.IDENTICAL_CONDITION)
              .level(RuleConflict.Level.WARN)
              .newRuleCode(newDefinition.getCode())
              .conflictingRuleCode(existing.getCode())
              .description("条件表达式完全相同（可能重复定义）: " + existingExpr)
              .build());
        }
      } else if (existing.getName() != null
          && existing.getName().equals(newDefinition.getName())
          && existing.getCategory() != null
          && existing.getCategory().equals(newDefinition.getCategory())) {
        conflicts.add(RuleConflict.builder()
            .type(RuleConflict.Type.NAME_COLLISION)
            .level(RuleConflict.Level.WARN)
            .newRuleCode(newDefinition.getCode())
            .conflictingRuleCode(existing.getCode())
            .description("同类别下名称相同: " + existing.getName())
            .build());
      }
    }
    return conflicts;
  }

  /**
   * 归一化表达式用于比较（去除空白、统一逻辑运算符、统一大小写、翻转反向比较）
   *
   * @param expression 条件表达式
   * @return 归一化后的表达式
   */
  private String normalizeExpression(String expression) {
    if (expression == null || expression.isBlank()) {
      return "";
    }
    String normalized = expression
        .replaceAll("\\s+", "")
        .replace("and", "&&")
        .replace("or", "||")
        .replace("not", "!")
        .toLowerCase();
    // 翻转反向比较： "3 < x" → "x > 3"
    Matcher revMatcher = REVERSE_COMPARISON_PATTERN.matcher(normalized);
    if (revMatcher.matches() && revMatcher.groupCount() >= CMP_GROUP_RIGHT) {
      String number = revMatcher.group(1);
      String op = revMatcher.group(2);
      String var = revMatcher.group(CMP_GROUP_RIGHT);
      // 反转比较方向
      String flippedOp = switch (op) {
        case ">" -> "<";
        case ">=" -> "<=";
        case "<" -> ">";
        case "<=" -> ">=";
        default -> op; // == and != are symmetric
      };
      normalized = var + flippedOp + number;
    }
    return normalized;
  }
}