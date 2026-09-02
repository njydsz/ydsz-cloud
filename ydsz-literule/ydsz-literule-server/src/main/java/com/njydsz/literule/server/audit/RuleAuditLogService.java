package com.njydsz.literule.server.audit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;

/**
 * 规则操作审计日志服务（P3-5 RBAC 与审计日志）
 *
 * <p>记录规则全生命周期操作（创建、修改、启停、回滚、审批、导入/导出等）， 支持 {@code who + when + what + before/after} 的完整审计链路。
 *
 * <h3>审计维度</h3>
 *
 * <ul>
 *   <li><b>操作人</b>（who）：谁执行了操作（工号/SSO 用户名）
 *   <li><b>操作时间</b>（when）：操作发生的时间
 *   <li><b>操作类型</b>（what）：CREATE / UPDATE / TOGGLE / ROLLBACK / APPROVE / REJECT / IMPORT / EXPORT
 *       / DELETE
 *   <li><b>变更内容</b>（before/after）：操作前后的规则定义快照（字段级 diff）
 *   <li><b>操作来源</b>（source）：MANUAL（手动）/ API（接口）/ SCHEDULED（定时）/ SDK（嵌入式）
 *   <li><b>操作结果</b>（result）：SUCCESS / FAILURE
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <p>消费方通过 SPI 注入 {@link AuditLogStore} 实现持久化，或使用默认的内存存储（适合测试）。
 *
 * <pre>{@code
 * RuleAuditLogService auditService = new RuleAuditLogService(auditLogStore);
 *
 * // 记录规则保存操作
 * auditService.logCreate(newDef, "zhangsan", "MANUAL");
 * auditService.logUpdate(oldDef, newDef, "zhangsan", "MANUAL", "修改阈值");
 * auditService.logToggle(ruleCode, false, true, "lisi", "API");
 * auditService.logRollback(ruleCode, 3, 2, "wangwu", "MANUAL");
 *
 * // 查询审计日志
 * List<AuditLogEntry> logs = auditService.queryByRuleCode("RISK_001", 50);
 * List<AuditLogEntry> userLogs = auditService.queryByOperator("zhangsan", 100);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RuleAuditLogService {

  private final AuditLogStore store;

  /**
   * 构造审计日志服务
   *
   * @param store 审计日志存储（为 null 时使用内存存储，仅适合测试）
   */
  public RuleAuditLogService(AuditLogStore store) {
    this.store = store != null ? store : new InMemoryAuditLogStore();
  }

  // ==================== 记录操作 ====================

  /** 记录规则创建
   * @param def 新规则定义
   * @param operator 操作人用户名（工号或SSO账号）
   * @param source 操作来源（MANUAL/API/SCHEDULED/SDK）
   */
  public void logCreate(RuleDefinitionDTO def, String operator, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(def.getCode())
            .ruleName(def.getName())
            .action(AuditAction.CREATE)
            .operator(operator)
            .source(source)
            .afterSnapshot(toSnapshot(def))
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则更新
   * @param oldDef 更新前规则定义
   * @param newDef 更新后规则定义
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param changeDesc 变更描述（如修改原因）
   */
  public void logUpdate(
      RuleDefinitionDTO oldDef,
      RuleDefinitionDTO newDef,
      String operator,
      String source,
      String changeDesc) {
    Map<String, FieldDiff> diffs = computeFieldDiff(oldDef, newDef);
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(newDef.getCode())
            .ruleName(newDef.getName())
            .action(AuditAction.UPDATE)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .beforeSnapshot(toSnapshot(oldDef))
            .afterSnapshot(toSnapshot(newDef))
            .fieldDiffs(diffs)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则启停切换
   * @param ruleCode 规则唯一编码
   * @param oldEnabled 切换前启用状态
   * @param newEnabled 切换后启用状态
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logToggle(
      String ruleCode, boolean oldEnabled, boolean newEnabled, String operator, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.TOGGLE)
            .operator(operator)
            .source(source)
            .changeDesc(String.format("enabled: %s -> %s", oldEnabled, newEnabled))
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则状态变更
   * @param ruleCode 规则唯一编码
   * @param oldStatus 变更前状态
   * @param newStatus 变更后状态
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logStatusChange(
      String ruleCode, String oldStatus, String newStatus, String operator, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.STATUS_CHANGE)
            .operator(operator)
            .source(source)
            .changeDesc(String.format("status: %s -> %s", oldStatus, newStatus))
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则回滚
   * @param ruleCode 规则唯一编码
   * @param fromVersion 回滚前版本号
   * @param toVersion 回滚目标版本号
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logRollback(
      String ruleCode, int fromVersion, int toVersion, String operator, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.ROLLBACK)
            .operator(operator)
            .source(source)
            .changeDesc(String.format("version: %d -> %d", fromVersion, toVersion))
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则审批通过
   * @param ruleCode 规则唯一编码
   * @param approver 审批人用户名
   * @param level 审批级别（如 L1/L2）
   * @param comment 审批意见
   * @param source 操作来源
   */
  public void logApprove(
      String ruleCode, String approver, String level, String comment, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.APPROVE)
            .operator(approver)
            .source(source)
            .changeDesc("审批通过 [" + level + "]: " + (comment != null ? comment : ""))
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则审批驳回
   * @param ruleCode 规则唯一编码
   * @param rejecter 驳回人用户名
   * @param level 审批级别
   * @param reason 驳回原因
   * @param source 操作来源
   */
  public void logReject(
      String ruleCode, String rejecter, String level, String reason, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.REJECT)
            .operator(rejecter)
            .source(source)
            .changeDesc("审批驳回 [" + level + "]: " + (reason != null ? reason : ""))
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则导入
   * @param ruleCode 首个导入规则编码
   * @param ruleName 首个导入规则名称
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param importedCount 导入规则总条数
   */
  public void logImport(
      String ruleCode, String ruleName, String operator, String source, int importedCount) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .ruleName(ruleName)
            .action(AuditAction.IMPORT)
            .operator(operator)
            .source(source)
            .changeDesc("导入 " + importedCount + " 条规则")
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则导出
   * @param ruleCode 导出规则编码
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param format 导出文件格式（JSON/YAML/EXCEL）
   */
  public void logExport(String ruleCode, String operator, String source, String format) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.EXPORT)
            .operator(operator)
            .source(source)
            .changeDesc("导出格式: " + format)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录规则删除
   * @param ruleCode 规则唯一编码
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logDelete(String ruleCode, String operator, String source) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.DELETE)
            .operator(operator)
            .source(source)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  /** 记录操作失败
   * @param ruleCode 规则唯一编码
   * @param action 审计操作类型
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param errorMessage 失败原因
   */
  public void logFailure(
      String ruleCode, AuditAction action, String operator, String source, String errorMessage) {
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(action)
            .operator(operator)
            .source(source)
            .result(AuditResult.FAILURE)
            .errorMessage(errorMessage)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry);
  }

  // ==================== 查询操作 ====================

  /** 按规则编码查询审计日志
   * @param ruleCode 规则唯一编码
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByRuleCode(String ruleCode, int limit) {
    return store.queryByRuleCode(ruleCode, limit);
  }

  /** 按操作人查询审计日志
   * @param operator 操作人用户名
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByOperator(String operator, int limit) {
    return store.queryByOperator(operator, limit);
  }

  /** 按操作类型查询审计日志
   * @param action 审计操作类型
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByAction(AuditAction action, int limit) {
    return store.queryByAction(action, limit);
  }

  /** 按时间范围查询审计日志
   * @param start 起始时间（含）
   * @param end 结束时间（不含）
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByTimeRange(LocalDateTime start, LocalDateTime end, int limit) {
    return store.queryByTimeRange(start, end, limit);
  }

  /** 查询最近的审计日志
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryRecent(int limit) {
    return store.queryRecent(limit);
  }

  // ==================== 内部方法 ====================

  private void record(AuditLogEntry entry) {
    try {
      store.save(entry);
      log.info(
          "[AuditLog] {} {} by {} from {}",
          entry.getAction(),
          entry.getRuleCode(),
          entry.getOperator(),
          entry.getSource());
    } catch (Exception e) {
      log.warn("[AuditLog] 审计日志记录失败: {}", e.getMessage());
    }
  }

  private Map<String, Object> toSnapshot(RuleDefinitionDTO def) {
    if (def == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> snapshot = new LinkedHashMap<>(16);
    snapshot.put("code", def.getCode());
    snapshot.put("name", def.getName());
    snapshot.put("conditionExpression", def.getConditionExpression());
    snapshot.put("severityExpression", def.getSeverityExpression());
    snapshot.put(
        "defaultSeverity",
        def.getDefaultSeverity() != null ? def.getDefaultSeverity().name() : null);
    snapshot.put("priority", def.getPriority());
    snapshot.put("enabled", def.isEnabled());
    snapshot.put("status", def.getStatus());
    snapshot.put("category", def.getCategory());
    snapshot.put("categoryPath", def.getCategoryPath());
    snapshot.put("owner", def.getOwner());
    snapshot.put("scope", def.getScope());
    snapshot.put("mutexGroup", def.getMutexGroup());
    snapshot.put("version", def.getVersion());
    return snapshot;
  }

  private Map<String, FieldDiff> computeFieldDiff(RuleDefinitionDTO oldDef, RuleDefinitionDTO newDef) {
    Map<String, FieldDiff> diffs = new LinkedHashMap<>(16);
    if (oldDef == null || newDef == null) {
      return diffs;
    }

    compareField(
        diffs,
        "conditionExpression",
        oldDef.getConditionExpression(),
        newDef.getConditionExpression());
    compareField(
        diffs,
        "severityExpression",
        oldDef.getSeverityExpression(),
        newDef.getSeverityExpression());
    compareField(
        diffs,
        "defaultSeverity",
        oldDef.getDefaultSeverity() != null ? oldDef.getDefaultSeverity().name() : null,
        newDef.getDefaultSeverity() != null ? newDef.getDefaultSeverity().name() : null);
    compareField(diffs, "priority", oldDef.getPriority(), newDef.getPriority());
    compareField(diffs, "enabled", oldDef.isEnabled(), newDef.isEnabled());
    compareField(diffs, "status", oldDef.getStatus(), newDef.getStatus());
    compareField(diffs, "category", oldDef.getCategory(), newDef.getCategory());
    compareField(diffs, "categoryPath", oldDef.getCategoryPath(), newDef.getCategoryPath());
    compareField(diffs, "owner", oldDef.getOwner(), newDef.getOwner());
    compareField(diffs, "scope", oldDef.getScope(), newDef.getScope());
    compareField(diffs, "mutexGroup", oldDef.getMutexGroup(), newDef.getMutexGroup());
    compareField(diffs, "titleTemplate", oldDef.getTitleTemplate(), newDef.getTitleTemplate());
    compareField(
        diffs,
        "descriptionTemplate",
        oldDef.getDescriptionTemplate(),
        newDef.getDescriptionTemplate());
    compareField(diffs, "description", oldDef.getDescription(), newDef.getDescription());
    return diffs;
  }

  private void compareField(
      Map<String, FieldDiff> diffs, String fieldName, Object oldValue, Object newValue) {
    if (!Objects.equals(oldValue, newValue)) {
      diffs.put(
          fieldName,
          FieldDiff.builder()
              .field(fieldName)
              .oldValue(oldValue != null ? oldValue.toString() : null)
              .newValue(newValue != null ? newValue.toString() : null)
              .build());
    }
  }

  // ==================== 枚举与模型 ====================

  /** 审计操作类型 */
  public enum AuditAction {
    /** 创建规则 */
    CREATE,
    /** 更新规则 */
    UPDATE,
    /** 规则启停切换 */
    TOGGLE,
    /** 规则状态变更 */
    STATUS_CHANGE,
    /** 规则版本回滚 */
    ROLLBACK,
    /** 审批通过 */
    APPROVE,
    /** 审批驳回 */
    REJECT,
    /** 规则导入 */
    IMPORT,
    /** 规则导出 */
    EXPORT,
    /** 规则删除 */
    DELETE,
    /** 规则试跑（dry-run） */
    DRY_RUN,
    /** 规则压测 */
    STRESS_TEST,
    /** 规则回放 */
    REPLAY
  }

  /** 审计结果 */
  public enum AuditResult {
    /** 操作成功 */
    SUCCESS,
    /** 操作失败 */
    FAILURE
  }

  /** 审计日志条目 */
  @Data
  @Builder
  public static class AuditLogEntry {
    /** 日志 ID */
    private String id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 操作类型 */
    private AuditAction action;

    /** 操作人 */
    private String operator;

    /** 操作来源 */
    private String source;

    /** 变更描述 */
    private String changeDesc;

    /** 操作前快照 */
    private Map<String, Object> beforeSnapshot;

    /** 操作后快照 */
    private Map<String, Object> afterSnapshot;

    /** 字段级差异 */
    private Map<String, FieldDiff> fieldDiffs;

    /** 操作结果 */
    private AuditResult result;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 操作时间 */
    private LocalDateTime createdAt;
  }

  /** 字段级差异 */
  @Data
  @Builder
  public static class FieldDiff {
    private String field;
    private String oldValue;
    private String newValue;
  }

  // ==================== 存储 SPI ====================

  /**
   * 审计日志存储接口（SPI）
   *
   * <p>由消费方提供实现，将审计日志写入数据库（如 {@code ydsz_rule_audit_log} 表）。 默认提供 {@link
   * InMemoryAuditLogStore}（仅适合测试）。
   */
  public interface AuditLogStore {

    void save(AuditLogEntry entry);

    List<AuditLogEntry> queryByRuleCode(String ruleCode, int limit);

    List<AuditLogEntry> queryByOperator(String operator, int limit);

    List<AuditLogEntry> queryByAction(AuditAction action, int limit);

    List<AuditLogEntry> queryByTimeRange(LocalDateTime start, LocalDateTime end, int limit);

    List<AuditLogEntry> queryRecent(int limit);
  }

  /** 内存审计日志存储（默认实现，仅适合测试） */
  public static class InMemoryAuditLogStore implements AuditLogStore {

    private final List<AuditLogEntry> entries = new CopyOnWriteArrayList<>();
    private final Map<String, List<AuditLogEntry>> byRuleCode = new ConcurrentHashMap<>();
    private final Map<String, List<AuditLogEntry>> byOperator = new ConcurrentHashMap<>();

    @Override
    public void save(AuditLogEntry entry) {
      entries.add(entry);
      byRuleCode.computeIfAbsent(entry.getRuleCode(), k -> new CopyOnWriteArrayList<>()).add(entry);
      if (entry.getOperator() != null) {
        byOperator
            .computeIfAbsent(entry.getOperator(), k -> new CopyOnWriteArrayList<>())
            .add(entry);
      }
    }

    @Override
    public List<AuditLogEntry> queryByRuleCode(String ruleCode, int limit) {
      List<AuditLogEntry> list = byRuleCode.getOrDefault(ruleCode, Collections.emptyList());
      return list.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<AuditLogEntry> queryByOperator(String operator, int limit) {
      List<AuditLogEntry> list = byOperator.getOrDefault(operator, Collections.emptyList());
      return list.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<AuditLogEntry> queryByAction(AuditAction action, int limit) {
      return entries.stream()
          .filter(e -> e.getAction() == action)
          .limit(limit)
          .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogEntry> queryByTimeRange(LocalDateTime start, LocalDateTime end, int limit) {
      return entries.stream()
          .filter(e -> e.getCreatedAt() != null)
          .filter(e -> !e.getCreatedAt().isBefore(start) && e.getCreatedAt().isBefore(end))
          .limit(limit)
          .collect(Collectors.toList());
    }

    @Override
    public List<AuditLogEntry> queryRecent(int limit) {
      int size = entries.size();
      int from = Math.max(0, size - limit);
      return new ArrayList<>(entries.subList(from, size));
    }
  }
}
