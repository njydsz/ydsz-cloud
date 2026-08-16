package com.njydsz.literule.server.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RetirementSuggestion;
import com.njydsz.literule.api.RollbackPreview;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.api.RuleStatus;
import com.njydsz.literule.server.config.LiteRuleProperties;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.spi.RuleConfigProvider;
import com.njydsz.literule.server.spi.RuleVersion;
import com.njydsz.literule.server.spi.RuleVersionRepository;

/**
 * 规则生命周期管理服务（P3-1）
 *
 * <p>对标国内主流规则引擎（如 Drools KIE Workbench、URule、阿里 QLExpressConsole） 的生命周期管理能力，提供规则退役检测与一键回滚预览功能。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li><b>退役检测</b>：基于规则执行统计自动识别应退役的规则 （休眠规则、高错误率、长期停用、低影响）
 *   <li><b>回滚预览</b>：在回滚前对比当前版本与目标版本的差异，生成预览报告
 *   <li><b>一键退役</b>：将规则从 PUBLISHED/DISABLED 状态归档（ARCHIVED）并禁用
 *   <li><b>批量退役</b>：批量执行退役操作
 *   <li><b>生命周期概览</b>：按状态维度统计规则分布
 * </ul>
 *
 * <h3>退役检测策略</h3>
 *
 * <ul>
 *   <li>{@link RetirementSuggestion.Reason#DORMANT}：评估次数 ≥ minEvaluations 且触发率 = 0
 *   <li>{@link RetirementSuggestion.Reason#HIGH_ERROR_RATE}：错误率 ≥ errorRateThreshold
 *   <li>{@link RetirementSuggestion.Reason#STALE_DISABLED}：已停用且停用时间 ≥ staleDisabledDays
 *   <li>{@link RetirementSuggestion.Reason#LOW_IMPACT}：触发率 < lowImpactTriggerRate 且评估次数 ≥
 *       minEvaluations
 * </ul>
 *
 * <h3>使用流程</h3>
 *
 * <pre>
 * // 1. 检测退役候选规则
 * List&lt;RetirementSuggestion&gt; suggestions = lifecycleService.detectRetirementCandidates();
 *
 * // 2. 预览回滚
 * RollbackPreview preview = lifecycleService.previewRollback("R001", 3);
 * System.out.println("差异数: " + preview.getDiffCount());
 *
 * // 3. 确认后执行回滚
 * RuleDefinition restored = lifecycleService.rollback("R001", 3, "admin");
 *
 * // 4. 一键退役
 * lifecycleService.retireRule("R002", "admin", "休眠规则，长期零触发");
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RuleLifecycleService {

  /** 日期时间格式 */
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** 默认休眠规则最小评估次数 */
  private static final long DEFAULT_DORMANT_MIN_EVALUATIONS = 1000;

  /** 默认高错误率阈值 */
  private static final double DEFAULT_HIGH_ERROR_RATE_THRESHOLD = 0.30;

  /** 默认长期停用天数 */
  private static final int DEFAULT_STALE_DISABLED_DAYS = 90;

  /** 默认低影响触发率阈值 */
  private static final double DEFAULT_LOW_IMPACT_TRIGGER_RATE = 0.001;

  /** 默认最小样本量（低于此值的规则不生成退役建议，因数据不足） */
  private static final long DEFAULT_MIN_SAMPLE_SIZE = 500;

  /** 置信度计算：最小样本对应的置信度 */
  private static final double BASE_CONFIDENCE = 0.3;

  /** 置信度计算：满样本对应的置信度 */
  private static final double FULL_CONFIDENCE = 0.95;

  /** 规则引擎实例，用于获取执行统计（评估次数/触发次数/错误次数）以驱动退役检测 */
  private final RuleEngine ruleEngine;

  /** 规则配置提供者（SPI），用于加载全部规则定义及按编码查询规则详情 */
  private final RuleConfigProvider configProvider;

  /** 规则管理服务，用于执行退役状态变更和回滚操作 */
  private final RuleAdminService ruleAdminService;

  /** 规则版本仓库（SPI），用于查询历史版本列表以支撑回滚预览；为 null 时不支持回滚 */
  private final RuleVersionRepository versionRepository;

  /** 休眠规则最小评估次数 */
  private long dormantMinEvaluations = DEFAULT_DORMANT_MIN_EVALUATIONS;

  /** 高错误率阈值 */
  private double highErrorRateThreshold = DEFAULT_HIGH_ERROR_RATE_THRESHOLD;

  /** 长期停用天数 */
  private int staleDisabledDays = DEFAULT_STALE_DISABLED_DAYS;

  /** 低影响触发率阈值 */
  private double lowImpactTriggerRate = DEFAULT_LOW_IMPACT_TRIGGER_RATE;

  /** 最小样本量 */
  private long minSampleSize = DEFAULT_MIN_SAMPLE_SIZE;

  /**
   * 构造规则生命周期管理服务
   *
   * @param ruleEngine 规则引擎
   * @param configProvider 规则配置提供者
   * @param ruleAdminService 规则管理服务
   * @param versionRepository 版本仓库（可为 null，为 null 时不支持回滚预览）
   */
  public RuleLifecycleService(
      RuleEngine ruleEngine,
      RuleConfigProvider configProvider,
      RuleAdminService ruleAdminService,
      RuleVersionRepository versionRepository) {
    this.ruleEngine = ruleEngine;
    this.configProvider = configProvider;
    this.ruleAdminService = ruleAdminService;
    this.versionRepository = versionRepository;
  }

  /**
   * 从配置属性初始化退役检测参数
   *
   * @param lifecycleConfig 生命周期配置
   */
  public void configure(LiteRuleProperties.LifecycleConfig lifecycleConfig) {
    if (lifecycleConfig == null) {
      return;
    }
    this.dormantMinEvaluations = lifecycleConfig.getDormantMinEvaluations();
    this.highErrorRateThreshold = lifecycleConfig.getHighErrorRateThreshold();
    this.staleDisabledDays = lifecycleConfig.getStaleDisabledDays();
    this.lowImpactTriggerRate = lifecycleConfig.getLowImpactTriggerRate();
    this.minSampleSize = lifecycleConfig.getMinSampleSize();
    log.info(
        "[Lifecycle] 规则生命周期管理服务已配置（dormantMin={}, errorRateThreshold={}, staleDays={}, lowImpactRate={}, minSample={}）",
        dormantMinEvaluations,
        highErrorRateThreshold,
        staleDisabledDays,
        lowImpactTriggerRate,
        minSampleSize);
  }

  // ==================== 退役检测 ====================

  /**
   * 检测退役候选规则
   *
   * <p>扫描全部规则，基于执行统计、生命周期状态生成退役建议。 已归档（ARCHIVED）的规则不参与检测。
   *
   * @return 退役建议列表（按置信度降序排列）
   */
  public List<RetirementSuggestion> detectRetirementCandidates() {
    List<RuleDefinition> allRules = configProvider.loadAllRules();
    if (allRules == null || allRules.isEmpty()) {
      return List.of();
    }

    // 获取执行统计
    RuleEngineStats stats = ruleEngine.getStats();
    Map<String, RuleEngineStats.RuleStat> perRuleStats =
        stats != null && stats.getPerRuleStats() != null
            ? stats.getPerRuleStats()
            : new HashMap<>();

    List<RetirementSuggestion> suggestions = new ArrayList<>();
    for (RuleDefinition rule : allRules) {
      // 已归档规则跳过
      RuleStatus status = RuleStatus.fromCode(rule.getStatus());
      if (status == RuleStatus.ARCHIVED) {
        continue;
      }

      RetirementSuggestion suggestion = analyzeRule(rule, perRuleStats.get(rule.getCode()));
      if (suggestion != null) {
        suggestions.add(suggestion);
      }
    }

    // 按置信度降序排列
    suggestions.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
    log.info("[Lifecycle] 退役检测完成：扫描 {} 条规则，发现 {} 条退役候选", allRules.size(), suggestions.size());
    return suggestions;
  }

  /**
   * 检测指定规则的退役建议
   *
   * @param ruleCode 规则编码
   * @return 退役建议；不符合退役条件返回 null
   */
  public RetirementSuggestion detectRetirement(String ruleCode) {
    if (ruleCode == null || ruleCode.isBlank()) {
      return null;
    }
    RuleDefinition rule = configProvider.findByCode(ruleCode);
    if (rule == null) {
      return null;
    }
    RuleStatus status = RuleStatus.fromCode(rule.getStatus());
    if (status == RuleStatus.ARCHIVED) {
      return null;
    }

    RuleEngineStats stats = ruleEngine.getStats();
    Map<String, RuleEngineStats.RuleStat> perRuleStats =
        stats != null && stats.getPerRuleStats() != null
            ? stats.getPerRuleStats()
            : new HashMap<>();

    return analyzeRule(rule, perRuleStats.get(ruleCode));
  }

  /** 分析单条规则是否应退役 */
  private RetirementSuggestion analyzeRule(RuleDefinition rule, RuleEngineStats.RuleStat stat) {
    long evaluations = stat != null ? stat.getExecutions() : 0;
    long triggered = stat != null ? stat.getTriggered() : 0;
    long errors = stat != null ? stat.getErrors() : 0;
    double triggerRate = evaluations > 0 ? (double) triggered / evaluations : 0.0;
    double errorRate = evaluations > 0 ? (double) errors / evaluations : 0.0;

    // 检查长期停用（不依赖执行统计）
    if (isStaleDisabled(rule)) {
      return buildSuggestion(
          rule,
          RetirementSuggestion.Reason.STALE_DISABLED,
          evaluations,
          triggered,
          errors,
          triggerRate,
          errorRate);
    }

    // 数据不足，不做退役判定
    if (evaluations < minSampleSize) {
      return null;
    }

    // 检查休眠规则
    if (evaluations >= dormantMinEvaluations && triggered == 0) {
      return buildSuggestion(
          rule,
          RetirementSuggestion.Reason.DORMANT,
          evaluations,
          triggered,
          errors,
          triggerRate,
          errorRate);
    }

    // 检查高错误率
    if (errorRate >= highErrorRateThreshold) {
      return buildSuggestion(
          rule,
          RetirementSuggestion.Reason.HIGH_ERROR_RATE,
          evaluations,
          triggered,
          errors,
          triggerRate,
          errorRate);
    }

    // 检查低影响
    if (triggerRate < lowImpactTriggerRate && triggered > 0) {
      return buildSuggestion(
          rule,
          RetirementSuggestion.Reason.LOW_IMPACT,
          evaluations,
          triggered,
          errors,
          triggerRate,
          errorRate);
    }

    return null;
  }

  /** 判断规则是否长期停用 */
  private boolean isStaleDisabled(RuleDefinition rule) {
    // 状态非 DISABLED 直接返回 false
    if (!"DISABLED".equalsIgnoreCase(rule.getStatus())) {
      return false;
    }
    // 检查 effectiveTo 或 reviewedAt
    LocalDateTime time = rule.getEffectiveTo();
    if (time == null) {
      time = rule.getReviewedAt();
    }
    if (time == null) {
      // 无法确定停用时间，保守判定为不长期停用
      return false;
    }
    try {
      long daysSince = Duration.between(time, LocalDateTime.now()).toDays();
      return daysSince >= staleDisabledDays;
    } catch (Exception e) {
      log.debug("[Lifecycle] 计算停用时长异常", e);
      return false;
    }
  }

  /** 构建退役建议 */
  private RetirementSuggestion buildSuggestion(
      RuleDefinition rule,
      RetirementSuggestion.Reason reason,
      long evaluations,
      long triggered,
      long errors,
      double triggerRate,
      double errorRate) {
    double confidence = calculateConfidence(evaluations);
    List<String> actions = buildRecommendedActions(reason, rule);

    String reasonDesc =
        switch (reason) {
          case DORMANT -> String.format("休眠规则：评估 %d 次零触发，规则可能已失效", evaluations);
          case HIGH_ERROR_RATE ->
              String.format("高错误率：错误率 %.1f%%（%d/%d），影响系统稳定性", errorRate * 100, errors, evaluations);
          case STALE_DISABLED -> String.format("长期停用：已停用超过 %d 天", staleDisabledDays);
          case LOW_IMPACT ->
              String.format("低影响：触发率 %.4f（%d/%d），投入产出比不合理", triggerRate, triggered, evaluations);
        };

    return RetirementSuggestion.builder()
        .ruleCode(rule.getCode())
        .ruleName(rule.getName())
        .category(rule.getCategory())
        .status(rule.getStatus())
        .reason(reason)
        .reasonDesc(reasonDesc)
        .totalEvaluations(evaluations)
        .totalTriggered(triggered)
        .totalErrors(errors)
        .triggerRate(triggerRate)
        .errorRate(errorRate)
        .recommendedActions(actions)
        .suggestedAt(LocalDateTime.now())
        .confidence(confidence)
        .build();
  }

  /** 计算置信度（基于样本量） */
  private double calculateConfidence(long evaluations) {
    if (evaluations < minSampleSize) {
      return BASE_CONFIDENCE;
    }
    // 样本量越大置信度越高，上限 FULL_CONFIDENCE
    double ratio = Math.log10(evaluations) / Math.log10(Math.max(minSampleSize * 10, 10_000));
    double confidence =
        BASE_CONFIDENCE + (FULL_CONFIDENCE - BASE_CONFIDENCE) * Math.min(1.0, ratio);
    return Math.round(confidence * 100) / 100.0;
  }

  /** 构建建议操作列表 */
  private List<String> buildRecommendedActions(
      RetirementSuggestion.Reason reason, RuleDefinition rule) {
    List<String> actions = new ArrayList<>();
    switch (reason) {
      case DORMANT -> {
        actions.add("检查规则条件表达式是否与当前业务场景匹配");
        actions.add("确认规则依赖的数据源是否正常");
        actions.add("建议归档并创建新规则替代");
      }
      case HIGH_ERROR_RATE -> {
        actions.add("检查规则表达式中的变量是否存在空值风险");
        actions.add("检查规则依赖的外部服务是否可用");
        actions.add("建议先停用规则，修复后再重新发布");
      }
      case STALE_DISABLED -> {
        actions.add("确认规则是否仍有业务价值");
        actions.add("建议归档以减少规则表膨胀");
      }
      case LOW_IMPACT -> {
        actions.add("评估规则条件是否过于严格");
        actions.add("考虑调整阈值或合并到其他规则");
        actions.add("如确认无价值，建议归档");
      }
    }
    return actions;
  }

  // ==================== 回滚预览 ====================

  /**
   * 预览回滚差异
   *
   * <p>在执行回滚前，对比当前版本与目标版本的规则定义，生成字段级差异报告。 前端可基于此报告展示变更项，经用户确认后执行回滚。
   *
   * @param ruleCode 规则编码
   * @param version 目标版本号
   * @return 回滚预览；规则不存在或版本不存在返回 null
   */
  public RollbackPreview previewRollback(String ruleCode, int version) {
    if (versionRepository == null) {
      return RollbackPreview.builder()
          .ruleCode(ruleCode)
          .targetVersion(version)
          .rollbackAllowed(false)
          .rollbackBlockedReason("版本仓库未配置，不支持回滚预览")
          .build();
    }

    RuleDefinition current = configProvider.findByCode(ruleCode);
    if (current == null) {
      return RollbackPreview.builder()
          .ruleCode(ruleCode)
          .targetVersion(version)
          .rollbackAllowed(false)
          .rollbackBlockedReason("规则不存在: " + ruleCode)
          .build();
    }

    // 查找目标版本
    List<RuleVersion> versions = versionRepository.listVersions(ruleCode);
    RuleVersion targetVersion =
        versions.stream().filter(v -> v.getVersion() == version).findFirst().orElse(null);

    if (targetVersion == null) {
      return RollbackPreview.builder()
          .ruleCode(ruleCode)
          .currentVersion(current.getVersion())
          .targetVersion(version)
          .rollbackAllowed(false)
          .rollbackBlockedReason("目标版本不存在: v" + version)
          .build();
    }

    // 检查是否允许回滚（ARCHIVED 状态不允许回滚）
    boolean allowed = true;
    String blockedReason = null;
    RuleStatus status = RuleStatus.fromCode(current.getStatus());
    if (status == RuleStatus.ARCHIVED) {
      allowed = false;
      blockedReason = "规则已归档，不允许回滚";
    }

    // 解析目标版本的规则定义 JSON
    RuleDefinition targetDef = parseDefinitionJson(targetVersion.getDefinitionJson(), ruleCode);

    // 生成字段差异
    List<RollbackPreview.FieldDiff> diffs = generateDiffs(current, targetDef);

    return RollbackPreview.builder()
        .ruleCode(ruleCode)
        .ruleName(current.getName())
        .currentVersion(current.getVersion())
        .targetVersion(version)
        .targetVersionOperator(targetVersion.getOperator())
        .targetVersionChangeDesc(targetVersion.getChangeDesc())
        .targetVersionCreatedAt(targetVersion.getCreatedAt())
        .rollbackAllowed(allowed)
        .rollbackBlockedReason(blockedReason)
        .diffs(diffs)
        .build();
  }

  /**
   * 解析版本 JSON 为 RuleDefinition
   *
   * <p>使用简化的 JSON 解析逻辑，提取关键字段进行比较。 如需精确解析，消费方可注入 ObjectMapper。
   */
  private RuleDefinition parseDefinitionJson(String json, String ruleCode) {
    if (json == null || json.isBlank()) {
      return RuleDefinition.builder().code(ruleCode).build();
    }
    try {
      // 简化解析：提取 JSON 中的字段值
      Map<String, String> fields = extractJsonFields(json);
      return RuleDefinition.builder()
          .code(ruleCode)
          .name(fields.get("name"))
          .description(fields.get("description"))
          .conditionExpression(fields.get("conditionExpression"))
          .severityExpression(fields.get("severityExpression"))
          .category(fields.get("category"))
          .categoryPath(fields.get("categoryPath"))
          .owner(fields.get("owner"))
          .scope(fields.get("scope"))
          .status(fields.getOrDefault("status", "PUBLISHED"))
          .environment(fields.getOrDefault("environment", "default"))
          .mutexGroup(fields.get("mutexGroup"))
          .titleTemplate(fields.get("titleTemplate"))
          .descriptionTemplate(fields.get("descriptionTemplate"))
          .effectiveFrom(fields.get("effectiveFrom"))
          .effectiveTo(fields.get("effectiveTo"))
          .build();
    } catch (Exception e) {
      log.warn("[Lifecycle] 解析版本 JSON 失败: {}", e.getMessage());
      return RuleDefinition.builder().code(ruleCode).build();
    }
  }

  /** 从 JSON 字符串中提取字段值（简化实现） */
  private Map<String, String> extractJsonFields(String json) {
    Map<String, String> fields = new LinkedHashMap<>();
    // 简化实现：逐个提取 "key":"value" 或 "key":value
    String[] keys = {
      "name",
      "description",
      "conditionExpression",
      "severityExpression",
      "category",
      "categoryPath",
      "owner",
      "scope",
      "status",
      "environment",
      "mutexGroup",
      "titleTemplate",
      "descriptionTemplate",
      "effectiveFrom",
      "effectiveTo",
      "defaultSeverity"
    };
    for (String key : keys) {
      String value = extractJsonValue(json, key);
      if (value != null) {
        fields.put(key, value);
      }
    }
    return fields;
  }

  /** 从 JSON 中提取单个字段的值 */
  private String extractJsonValue(String json, String key) {
    String pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) {
      return null;
    }
    int colonIdx = json.indexOf(":", idx + pattern.length());
    if (colonIdx < 0) {
      return null;
    }
    int start = colonIdx + 1;
    // 跳过空白
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
      start++;
    }
    if (start >= json.length()) {
      return null;
    }
    if (json.charAt(start) == '"') {
      // 字符串值
      int end = json.indexOf("\"", start + 1);
      if (end < 0) {
        return null;
      }
      return json.substring(start + 1, end);
    } else {
      // 非字符串值（数字、布尔）
      int end = start;
      while (end < json.length()
          && json.charAt(end) != ','
          && json.charAt(end) != '}'
          && json.charAt(end) != ']') {
        end++;
      }
      return json.substring(start, end).trim();
    }
  }

  /** 生成字段差异列表 */
  private List<RollbackPreview.FieldDiff> generateDiffs(
      RuleDefinition current, RuleDefinition target) {
    List<RollbackPreview.FieldDiff> diffs = new ArrayList<>();
    compareField(diffs, "name", "规则名称", current.getName(), target.getName());
    compareField(diffs, "description", "描述", current.getDescription(), target.getDescription());
    compareField(
        diffs,
        "conditionExpression",
        "条件表达式",
        current.getConditionExpression(),
        target.getConditionExpression());
    compareField(
        diffs,
        "severityExpression",
        "严重度表达式",
        current.getSeverityExpression(),
        target.getSeverityExpression());
    compareField(diffs, "category", "分类", current.getCategory(), target.getCategory());
    compareField(
        diffs, "categoryPath", "分类路径", current.getCategoryPath(), target.getCategoryPath());
    compareField(diffs, "owner", "责任人", current.getOwner(), target.getOwner());
    compareField(diffs, "scope", "影响范围", current.getScope(), target.getScope());
    compareField(diffs, "status", "状态", current.getStatus(), target.getStatus());
    compareField(diffs, "environment", "环境", current.getEnvironment(), target.getEnvironment());
    compareField(diffs, "mutexGroup", "互斥组", current.getMutexGroup(), target.getMutexGroup());
    compareField(
        diffs, "titleTemplate", "标题模板", current.getTitleTemplate(), target.getTitleTemplate());
    compareField(
        diffs,
        "descriptionTemplate",
        "描述模板",
        current.getDescriptionTemplate(),
        target.getDescriptionTemplate());
    compareField(
        diffs,
        "priority",
        "优先级",
        String.valueOf(current.getPriority()),
        String.valueOf(target.getPriority()));
    compareField(
        diffs,
        "defaultSeverity",
        "默认严重度",
        current.getDefaultSeverity() != null ? current.getDefaultSeverity().name() : null,
        target.getDefaultSeverity() != null ? target.getDefaultSeverity().name() : null);
    return diffs;
  }

  /** 比较单个字段并添加差异 */
  private void compareField(
      List<RollbackPreview.FieldDiff> diffs,
      String field,
      String label,
      String currentValue,
      String targetValue) {
    String cur = normalizeValue(currentValue);
    String tgt = normalizeValue(targetValue);
    if (!equals(cur, tgt)) {
      RollbackPreview.DiffType type;
      if (cur == null && tgt != null) {
        type = RollbackPreview.DiffType.ADDED;
      } else if (cur != null && tgt == null) {
        type = RollbackPreview.DiffType.REMOVED;
      } else {
        type = RollbackPreview.DiffType.MODIFIED;
      }
      diffs.add(
          RollbackPreview.FieldDiff.builder()
              .field(field)
              .fieldLabel(label)
              .currentValue(cur)
              .targetValue(tgt)
              .diffType(type)
              .build());
    }
  }

  private String normalizeValue(String value) {
    if (value == null || value.isBlank() || "null".equals(value)) {
      return null;
    }
    return value.trim();
  }

  private boolean equals(String a, String b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }

  // ==================== 一键回滚 ====================

  /**
   * 执行一键回滚
   *
   * <p>回滚前会自动执行预览校验，确认回滚安全性后委托 {@link RuleAdminService#rollback} 执行实际回滚操作。
   *
   * @param ruleCode 规则编码
   * @param version 目标版本号
   * @param operator 操作人
   * @return 回滚后的规则定义
   * @throws IllegalStateException 规则已归档或版本不存在
   */
  public RuleDefinition rollback(String ruleCode, int version, String operator) {
    RollbackPreview preview = previewRollback(ruleCode, version);
    if (!preview.isRollbackAllowed()) {
      throw new IllegalStateException("回滚被拒绝: " + preview.getRollbackBlockedReason());
    }
    log.info(
        "[Lifecycle] 执行一键回滚: rule={}, targetVersion={}, diffCount={}, operator={}",
        ruleCode,
        version,
        preview.getDiffCount(),
        operator);
    return ruleAdminService.rollback(ruleCode, version, operator);
  }

  // ==================== 一键退役 ====================

  /**
   * 一键退役规则
   *
   * <p>将规则状态变更为 ARCHIVED 并禁用。归档后规则不再参与评估， 但保留版本历史以备审计和恢复。
   *
   * @param ruleCode 规则编码
   * @param operator 操作人
   * @param reason 退役原因
   * @return 退役后的规则定义
   * @throws IllegalStateException 规则已归档
   */
  public RuleDefinition retireRule(String ruleCode, String operator, String reason) {
    RuleDefinition rule = configProvider.findByCode(ruleCode);
    if (rule == null) {
      throw new IllegalArgumentException("规则不存在: " + ruleCode);
    }

    RuleStatus currentStatus = RuleStatus.fromCode(rule.getStatus());
    if (currentStatus == RuleStatus.ARCHIVED) {
      throw new IllegalStateException("规则已归档，无需重复退役: " + ruleCode);
    }

    // 校验状态转换合法性
    if (currentStatus != null && !currentStatus.canTransitionTo(RuleStatus.ARCHIVED)) {
      throw new IllegalStateException("不允许的状态转换: " + currentStatus.getDesc() + " → 已归档（请先停用或发布规则）");
    }

    // 设置归档状态并禁用
    rule.setStatus(RuleStatus.ARCHIVED.name());
    rule.setEnabled(false);
    rule.setReviewComment("退役原因: " + reason);

    RuleDefinition saved = ruleAdminService.save(rule, operator, "规则退役: " + reason);
    log.info("[Lifecycle] 规则已退役: code={}, operator={}, reason={}", ruleCode, operator, reason);
    return saved;
  }

  /**
   * 批量退役规则
   *
   * @param ruleCodes 规则编码列表
   * @param operator 操作人
   * @param reason 退役原因
   * @return 退役结果（ruleCode → 成功/失败信息）
   */
  public Map<String, String> bulkRetire(List<String> ruleCodes, String operator, String reason) {
    Map<String, String> results = new LinkedHashMap<>();
    if (ruleCodes == null || ruleCodes.isEmpty()) {
      return results;
    }
    int success = 0;
    int failed = 0;
    for (String code : ruleCodes) {
      try {
        retireRule(code, operator, reason);
        results.put(code, "SUCCESS");
        success++;
      } catch (Exception e) {
        results.put(code, "FAILED: " + e.getMessage());
        failed++;
        log.warn("[Lifecycle] 批量退役失败: code={}, error={}", code, e.getMessage());
      }
    }
    log.info(
        "[Lifecycle] 批量退役完成: total={}, success={}, failed={}", ruleCodes.size(), success, failed);
    return results;
  }

  // ==================== 生命周期概览 ====================

  /**
   * 生成规则生命周期概览
   *
   * <p>按状态维度统计规则数量分布，用于前端仪表盘展示。
   *
   * @return 状态 → 数量 的映射
   */
  public Map<String, Integer> getLifecycleSummary() {
    List<RuleDefinition> allRules = configProvider.loadAllRules();
    Map<String, Integer> summary = new LinkedHashMap<>();

    // 初始化所有状态
    for (RuleStatus status : RuleStatus.values()) {
      summary.put(status.name(), 0);
    }

    if (allRules != null) {
      for (RuleDefinition rule : allRules) {
        String status = rule.getStatus();
        if (status == null || status.isBlank()) {
          status = "PUBLISHED";
        }
        summary.merge(status, 1, (a, b) -> Integer.valueOf(a.intValue() + b.intValue()));
      }
    }

    return summary;
  }

  /**
   * 获取需要关注的规则数量（退役候选数）
   *
   * @return 退役候选规则数量
   */
  public int getRetirementCandidateCount() {
    return detectRetirementCandidates().size();
  }

  // ==================== Getter / Setter ====================

  public long getDormantMinEvaluations() {
    return dormantMinEvaluations;
  }

  public void setDormantMinEvaluations(long dormantMinEvaluations) {
    this.dormantMinEvaluations = dormantMinEvaluations;
  }

  public double getHighErrorRateThreshold() {
    return highErrorRateThreshold;
  }

  public void setHighErrorRateThreshold(double highErrorRateThreshold) {
    this.highErrorRateThreshold = highErrorRateThreshold;
  }

  public int getStaleDisabledDays() {
    return staleDisabledDays;
  }

  public void setStaleDisabledDays(int staleDisabledDays) {
    this.staleDisabledDays = staleDisabledDays;
  }

  public double getLowImpactTriggerRate() {
    return lowImpactTriggerRate;
  }

  public void setLowImpactTriggerRate(double lowImpactTriggerRate) {
    this.lowImpactTriggerRate = lowImpactTriggerRate;
  }

  public long getMinSampleSize() {
    return minSampleSize;
  }

  public void setMinSampleSize(long minSampleSize) {
    this.minSampleSize = minSampleSize;
  }
}
