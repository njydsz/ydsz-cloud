package com.njydsz.literule.server.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.literule.domain.dto.RuleDefinition;
import com.njydsz.literule.domain.enums.RuleSeverity;

/**
 * 规则版本 Diff 服务
 *
 * <p>对两个 {@link RuleDefinition} 进行字段级结构化对比，产出 {@link RuleVersionDiff}。 支持的字段对比维度：
 *
 * <ul>
 *   <li>基本信息：code, name, category, categoryPath, description, owner, scope
 *   <li>表达式：conditionExpression, severityExpression, titleTemplate, descriptionTemplate
 *   <li>执行配置：priority, enabled, mutexGroup, defaultSeverity
 *   <li>灰度配置：canaryRatio, canaryConditionExpression, canarySeverityExpression
 *   <li>生命周期：status, effectiveFrom, effectiveTo, environment
 * </ul>
 *
 * <p>表达式类字段的 Diff 后续可结合 AST 级语义对比（P3-4 规则冲突检测增强）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class RuleVersionDiffService {

  /**
   * 对比两个规则定义
   *
   * @param oldDef 旧版本定义
   * @param newDef 新版本定义
   * @return Diff 结果
   */
  public RuleVersionDiff diff(RuleDefinition oldDef, RuleDefinition newDef) {
    if (oldDef == null && newDef == null) {
      return RuleVersionDiff.builder().entries(List.of()).summary("两个版本均为空").build();
    }
    if (oldDef == null) {
      return RuleVersionDiff.builder()
          .newVersion(newDef.getVersion())
          .ruleCode(newDef.getCode())
          .entries(
              List.of(
                  RuleVersionDiff.DiffEntry.builder()
                      .type(RuleVersionDiff.DiffType.ADDED)
                      .field("entire_rule")
                      .fieldLabel("整条规则")
                      .oldValue(null)
                      .newValue(newDef.getName())
                      .build()))
          .summary("新增规则: " + newDef.getName())
          .build();
    }
    if (newDef == null) {
      return RuleVersionDiff.builder()
          .oldVersion(oldDef.getVersion())
          .ruleCode(oldDef.getCode())
          .entries(
              List.of(
                  RuleVersionDiff.DiffEntry.builder()
                      .type(RuleVersionDiff.DiffType.REMOVED)
                      .field("entire_rule")
                      .fieldLabel("整条规则")
                      .oldValue(oldDef.getName())
                      .newValue(null)
                      .build()))
          .summary("删除规则: " + oldDef.getName())
          .build();
    }

    List<RuleVersionDiff.DiffEntry> entries = new ArrayList<>();

    // 基本信息
    compareField(entries, "code", "规则编码", oldDef.getCode(), newDef.getCode());
    compareField(entries, "name", "规则名称", oldDef.getName(), newDef.getName());
    compareField(entries, "category", "规则类别", oldDef.getCategory(), newDef.getCategory());
    compareField(
        entries, "categoryPath", "分类路径", oldDef.getCategoryPath(), newDef.getCategoryPath());
    compareField(entries, "description", "规则描述", oldDef.getDescription(), newDef.getDescription());
    compareField(entries, "owner", "责任人", oldDef.getOwner(), newDef.getOwner());
    compareField(entries, "scope", "影响范围", oldDef.getScope(), newDef.getScope());

    // 表达式
    compareField(
        entries,
        "conditionExpression",
        "条件表达式",
        oldDef.getConditionExpression(),
        newDef.getConditionExpression());
    compareField(
        entries,
        "severityExpression",
        "严重度表达式",
        oldDef.getSeverityExpression(),
        newDef.getSeverityExpression());
    compareField(
        entries, "titleTemplate", "标题模板", oldDef.getTitleTemplate(), newDef.getTitleTemplate());
    compareField(
        entries,
        "descriptionTemplate",
        "描述模板",
        oldDef.getDescriptionTemplate(),
        newDef.getDescriptionTemplate());

    // 执行配置
    compareField(entries, "priority", "优先级", oldDef.getPriority(), newDef.getPriority());
    compareField(entries, "enabled", "是否启用", oldDef.isEnabled(), newDef.isEnabled());
    compareField(entries, "mutexGroup", "互斥组", oldDef.getMutexGroup(), newDef.getMutexGroup());
    compareSeverity(
        entries,
        "defaultSeverity",
        "默认严重度",
        oldDef.getDefaultSeverity(),
        newDef.getDefaultSeverity());

    // 灰度配置
    compareField(entries, "canaryRatio", "灰度比例", oldDef.getCanaryRatio(), newDef.getCanaryRatio());
    compareField(
        entries,
        "canaryConditionExpression",
        "灰度条件表达式",
        oldDef.getCanaryConditionExpression(),
        newDef.getCanaryConditionExpression());
    compareField(
        entries,
        "canarySeverityExpression",
        "灰度严重度表达式",
        oldDef.getCanarySeverityExpression(),
        newDef.getCanarySeverityExpression());

    // 生命周期
    compareField(entries, "status", "状态", oldDef.getStatus(), newDef.getStatus());
    compareField(
        entries, "effectiveFrom", "生效时间", oldDef.getEffectiveFrom(), newDef.getEffectiveFrom());
    compareField(entries, "effectiveTo", "失效时间", oldDef.getEffectiveTo(), newDef.getEffectiveTo());
    compareField(entries, "environment", "环境标识", oldDef.getEnvironment(), newDef.getEnvironment());

    String summary = buildSummary(oldDef, newDef, entries);

    return RuleVersionDiff.builder()
        .oldVersion(oldDef.getVersion())
        .newVersion(newDef.getVersion())
        .ruleCode(newDef.getCode())
        .entries(entries)
        .summary(summary)
        .build();
  }

  /** 对比单个字段（字符串类型） */
  private void compareField(
      List<RuleVersionDiff.DiffEntry> entries,
      String field,
      String label,
      Object oldVal,
      Object newVal) {
    String oldStr = oldVal == null ? null : String.valueOf(oldVal);
    String newStr = newVal == null ? null : String.valueOf(newVal);
    if (Objects.equals(oldStr, newStr)) {
      return; // 跳过未变更字段，减少结果体积
    }
    entries.add(
        RuleVersionDiff.DiffEntry.builder()
            .type(
                oldStr == null
                    ? RuleVersionDiff.DiffType.ADDED
                    : newStr == null
                        ? RuleVersionDiff.DiffType.REMOVED
                        : RuleVersionDiff.DiffType.MODIFIED)
            .field(field)
            .fieldLabel(label)
            .oldValue(oldStr)
            .newValue(newStr)
            .build());
  }

  /** 对比严重度 */
  private void compareSeverity(
      List<RuleVersionDiff.DiffEntry> entries,
      String field,
      String label,
      RuleSeverity oldVal,
      RuleSeverity newVal) {
    String oldStr = oldVal == null ? null : oldVal.name();
    String newStr = newVal == null ? null : newVal.name();
    if (Objects.equals(oldStr, newStr)) {
      return;
    }
    entries.add(
        RuleVersionDiff.DiffEntry.builder()
            .type(
                oldStr == null
                    ? RuleVersionDiff.DiffType.ADDED
                    : newStr == null
                        ? RuleVersionDiff.DiffType.REMOVED
                        : RuleVersionDiff.DiffType.MODIFIED)
            .field(field)
            .fieldLabel(label)
            .oldValue(oldStr)
            .newValue(newStr)
            .build());
  }

  /** 构建变更摘要 */
  private String buildSummary(
      RuleDefinition oldDef, RuleDefinition newDef, List<RuleVersionDiff.DiffEntry> entries) {
    int modified =
        (int)
            entries.stream().filter(e -> e.getType() == RuleVersionDiff.DiffType.MODIFIED).count();
    int added =
        (int) entries.stream().filter(e -> e.getType() == RuleVersionDiff.DiffType.ADDED).count();
    int removed =
        (int) entries.stream().filter(e -> e.getType() == RuleVersionDiff.DiffType.REMOVED).count();
    return String.format(
        "v%d → v%d: %d 项修改, %d 项新增, %d 项删除",
        oldDef.getVersion(), newDef.getVersion(), modified, added, removed);
  }
}
