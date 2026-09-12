package com.njydsz.literule.server.audit;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.audit.enums.AuditStatus;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;

/**
 * 规则操作审计日志服务（P3-5 RBAC 与审计日志）
 *
 * <p>记录规则全生命周期操作（创建、修改、启停、回滚、审批、导入/导出等）， 支持 {@code who + when + what + before/after} 的完整审计链路。
 *
 * <p>底层委托 ydsz-common-audit 的 {@link AuditRecorder}（写入）和 {@link AuditQueryService}（查询）， 使用统一的 {@code sys_audit_log} 表持久化，替代自建的内存存储实现。
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
 * <pre>{@code
 * RuleAuditLogService auditService = new RuleAuditLogService(auditRecorder, auditQueryService);
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

  /** 审计模块名（对应 sys_audit_log.module 字段，用于查询过滤） */
  private static final String MODULE_RULE_ENGINE = "规则引擎";

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** "source=" 前缀长度（避免魔法值） */
  private static final int SOURCE_PREFIX_LENGTH = "source=".length();

  /** 审计日志写入器（由 ydsz-common-audit 自动配置提供） */
  private final AuditRecorder auditRecorder;

  /** 审计日志查询服务（由 ydsz-common-audit 自动配置提供） */
  private final AuditQueryService auditQueryService;

  /**
   * 构造审计日志服务
   *
   * @param auditRecorder 审计日志写入器（由 ydsz-common-audit 提供，可为 null — 此时写入降级为日志输出）
   * @param auditQueryService 审计日志查询服务（由 ydsz-common-audit 提供，可为 null — 此时查询返回空列表）
   */
  public RuleAuditLogService(AuditRecorder auditRecorder, AuditQueryService auditQueryService) {
    this.auditRecorder = auditRecorder;
    this.auditQueryService = auditQueryService;
  }

  // ==================== 记录操作 ====================

  /**
   * 记录规则创建
   *
   * @param def 新规则定义
   * @param operator 操作人用户名（工号或 SSO 账号）
   * @param source 操作来源（MANUAL/API/SCHEDULED/SDK）
   */
  public void logCreate(RuleDefinitionDTO def, String operator, String source) {
    Map<String, Object> afterSnapshot = toSnapshot(def);
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(def.getCode())
            .ruleName(def.getName())
            .action(AuditAction.CREATE)
            .operator(operator)
            .source(source)
            .afterSnapshot(afterSnapshot)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.CREATE,
        null,
        afterSnapshot,
        null,
        null);
  }

  /**
   * 记录规则更新
   *
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
    Map<String, Object> beforeSnapshot = toSnapshot(oldDef);
    Map<String, Object> afterSnapshot = toSnapshot(newDef);
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(newDef.getCode())
            .ruleName(newDef.getName())
            .action(AuditAction.UPDATE)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .beforeSnapshot(beforeSnapshot)
            .afterSnapshot(afterSnapshot)
            .fieldDiffs(diffs)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.UPDATE,
        beforeSnapshot,
        afterSnapshot,
        null,
        null);
  }

  /**
   * 记录规则启停切换
   *
   * @param ruleCode 规则唯一编码
   * @param oldEnabled 切换前启用状态
   * @param newEnabled 切换后启用状态
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logToggle(
      String ruleCode, boolean oldEnabled, boolean newEnabled, String operator, String source) {
    String changeDesc = String.format("enabled: %s -> %s", oldEnabled, newEnabled);
    com.njydsz.common.audit.enums.AuditAction commonAction =
        newEnabled
            ? com.njydsz.common.audit.enums.AuditAction.ENABLE
            : com.njydsz.common.audit.enums.AuditAction.DISABLE;
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.TOGGLE)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(entry, commonAction, null, null, null, null);
  }

  /**
   * 记录规则状态变更
   *
   * @param ruleCode 规则唯一编码
   * @param oldStatus 变更前状态
   * @param newStatus 变更后状态
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logStatusChange(
      String ruleCode, String oldStatus, String newStatus, String operator, String source) {
    String changeDesc = String.format("status: %s -> %s", oldStatus, newStatus);
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.STATUS_CHANGE)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.OTHER,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录规则回滚
   *
   * @param ruleCode 规则唯一编码
   * @param fromVersion 回滚前版本号
   * @param toVersion 回滚目标版本号
   * @param operator 操作人用户名
   * @param source 操作来源
   */
  public void logRollback(
      String ruleCode, int fromVersion, int toVersion, String operator, String source) {
    String changeDesc = String.format("version: %d -> %d", fromVersion, toVersion);
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.ROLLBACK)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.RESTORE,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录规则审批通过
   *
   * @param ruleCode 规则唯一编码
   * @param approver 审批人用户名
   * @param level 审批级别（如 L1/L2）
   * @param comment 审批意见
   * @param source 操作来源
   */
  public void logApprove(
      String ruleCode, String approver, String level, String comment, String source) {
    String changeDesc = "审批通过 [" + level + "]: " + (comment != null ? comment : "");
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.APPROVE)
            .operator(approver)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.APPROVE,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录规则审批驳回
   *
   * @param ruleCode 规则唯一编码
   * @param rejecter 驳回人用户名
   * @param level 审批级别
   * @param reason 驳回原因
   * @param source 操作来源
   */
  public void logReject(
      String ruleCode, String rejecter, String level, String reason, String source) {
    String changeDesc = "审批驳回 [" + level + "]: " + (reason != null ? reason : "");
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.REJECT)
            .operator(rejecter)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.REJECT,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录规则导入
   *
   * @param ruleCode 首个导入规则编码
   * @param ruleName 首个导入规则名称
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param importedCount 导入规则总条数
   */
  public void logImport(
      String ruleCode, String ruleName, String operator, String source, int importedCount) {
    String changeDesc = "导入 " + importedCount + " 条规则";
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .ruleName(ruleName)
            .action(AuditAction.IMPORT)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.IMPORT,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录规则导出
   *
   * @param ruleCode 导出规则编码
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param format 导出文件格式（JSON/YAML/EXCEL）
   */
  public void logExport(String ruleCode, String operator, String source, String format) {
    String changeDesc = "导出格式: " + format;
    AuditLogEntry entry =
        AuditLogEntry.builder()
            .ruleCode(ruleCode)
            .action(AuditAction.EXPORT)
            .operator(operator)
            .source(source)
            .changeDesc(changeDesc)
            .result(AuditResult.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.EXPORT,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录规则删除
   *
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
    record(
        entry,
        com.njydsz.common.audit.enums.AuditAction.DELETE,
        null,
        null,
        null,
        null);
  }

  /**
   * 记录操作失败
   *
   * @param ruleCode 规则唯一编码
   * @param action 审计操作类型
   * @param operator 操作人用户名
   * @param source 操作来源
   * @param errorMessage 失败原因
   */
  public void logFailure(
      String ruleCode,
      AuditAction action,
      String operator,
      String source,
      String errorMessage) {
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
    record(
        entry,
        toCommonAction(action),
        null,
        null,
        AuditStatus.FAILURE,
        errorMessage);
  }

  // ==================== 查询操作 ====================

  /**
   * 按规则编码查询审计日志
   *
   * @param ruleCode 规则唯一编码
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByRuleCode(String ruleCode, int limit) {
    if (auditQueryService == null) {
      return Collections.emptyList();
    }
    List<AuditLog> logs = auditQueryService.getByBusinessNo(ruleCode);
    return logs.stream().limit(limit).map(this::toAuditLogEntry).collect(Collectors.toList());
  }

  /**
   * 按操作人查询审计日志
   *
   * @param operator 操作人用户名
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByOperator(String operator, int limit) {
    if (auditQueryService == null) {
      return Collections.emptyList();
    }
    List<AuditLog> logs = auditQueryService.getByOperator(operator, null, null);
    return logs.stream().limit(limit).map(this::toAuditLogEntry).collect(Collectors.toList());
  }

  /**
   * 按操作类型查询审计日志
   *
   * @param action 审计操作类型
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByAction(AuditAction action, int limit) {
    if (auditQueryService == null) {
      return Collections.emptyList();
    }
    com.njydsz.common.audit.enums.AuditAction commonAction = toCommonAction(action);
    List<AuditLog> allLogs = auditQueryService.getByTimeRange(null, null);
    return allLogs.stream()
        .filter(log -> log.getAction() != null && log.getAction().equals(commonAction.getCode()))
        .limit(limit)
        .map(this::toAuditLogEntry)
        .collect(Collectors.toList());
  }

  /**
   * 按时间范围查询审计日志
   *
   * @param start 起始时间（含）
   * @param end 结束时间（不含）
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryByTimeRange(LocalDateTime start, LocalDateTime end, int limit) {
    if (auditQueryService == null) {
      return Collections.emptyList();
    }
    List<AuditLog> logs = auditQueryService.getByTimeRange(start, end);
    return logs.stream().limit(limit).map(this::toAuditLogEntry).collect(Collectors.toList());
  }

  /**
   * 查询最近的审计日志
   *
   * @param limit 返回条数上限
   * @return 审计日志列表（按时间倒序）
   */
  public List<AuditLogEntry> queryRecent(int limit) {
    return queryByTimeRange(null, null, limit);
  }

  // ==================== 内部方法 ====================

  /**
   * 将自建审计操作枚举映射为通用审计操作枚举
   *
   * @param action 自建审计操作枚举
   * @return 通用审计操作枚举
   */
  private com.njydsz.common.audit.enums.AuditAction toCommonAction(AuditAction action) {
    if (action == null) {
      return com.njydsz.common.audit.enums.AuditAction.OTHER;
    }
    switch (action) {
      case CREATE:
        return com.njydsz.common.audit.enums.AuditAction.CREATE;
      case UPDATE:
        return com.njydsz.common.audit.enums.AuditAction.UPDATE;
      case TOGGLE:
        return com.njydsz.common.audit.enums.AuditAction.ENABLE;
      case STATUS_CHANGE:
        return com.njydsz.common.audit.enums.AuditAction.UPDATE;
      case ROLLBACK:
        return com.njydsz.common.audit.enums.AuditAction.RESTORE;
      case APPROVE:
        return com.njydsz.common.audit.enums.AuditAction.APPROVE;
      case REJECT:
        return com.njydsz.common.audit.enums.AuditAction.REJECT;
      case IMPORT:
        return com.njydsz.common.audit.enums.AuditAction.IMPORT;
      case EXPORT:
        return com.njydsz.common.audit.enums.AuditAction.EXPORT;
      case DELETE:
        return com.njydsz.common.audit.enums.AuditAction.DELETE;
      case DRY_RUN:
        return com.njydsz.common.audit.enums.AuditAction.OTHER;
      case STRESS_TEST:
        return com.njydsz.common.audit.enums.AuditAction.OTHER;
      case REPLAY:
        return com.njydsz.common.audit.enums.AuditAction.OTHER;
      default:
        return com.njydsz.common.audit.enums.AuditAction.OTHER;
    }
  }

  /**
   * 将 AuditLog（通用审计实体）映射回 AuditLogEntry（自建视图）
   *
   * @param log 通用审计日志实体
   * @return 审计日志条目
   */
  private AuditLogEntry toAuditLogEntry(AuditLog auditLog) {
    AuditLogEntry entry = new AuditLogEntry();
    entry.setId(auditLog.getId());
    entry.setRuleCode(auditLog.getBusinessNo());
    entry.setAction(fromCommonActionCode(auditLog.getAction()));
    entry.setOperator(auditLog.getOperatorId());
    entry.setResult(fromCommonStatus(auditLog.getStatus()));
    entry.setErrorMessage(auditLog.getErrorMessage());
    entry.setCreatedAt(auditLog.getOperationTime());

    // 从 diffBeforeSnapshot 还原 beforeSnapshot
    if (auditLog.getDiffBeforeSnapshot() != null && !auditLog.getDiffBeforeSnapshot().isEmpty()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> before =
            YdszJson.fromJson(auditLog.getDiffBeforeSnapshot(), Map.class);
        entry.setBeforeSnapshot(before);
      } catch (Exception e) {
        RuleAuditLogService.log.debug("[AuditLog] 反序列化 beforeSnapshot 失败: {}", e.getMessage());
      }
    }

    // 从 diffAfterSnapshot 还原 afterSnapshot
    if (auditLog.getDiffAfterSnapshot() != null && !auditLog.getDiffAfterSnapshot().isEmpty()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> after =
            YdszJson.fromJson(auditLog.getDiffAfterSnapshot(), Map.class);
        entry.setAfterSnapshot(after);
      } catch (Exception e) {
        RuleAuditLogService.log.debug("[AuditLog] 反序列化 afterSnapshot 失败: {}", e.getMessage());
      }
    }

    // 从 content 解析 source 和 changeDesc
    if (auditLog.getContent() != null && !auditLog.getContent().isEmpty()) {
      parseContent(auditLog.getContent(), entry);
    }

    return entry;
  }

  /**
   * 从通用审计操作编码映射回自建审计操作枚举
   *
   * @param actionCode 通用审计操作编码
   * @return 自建审计操作枚举
   */
  private AuditAction fromCommonActionCode(Integer actionCode) {
    if (actionCode == null) {
      return AuditAction.STATUS_CHANGE;
    }
    for (com.njydsz.common.audit.enums.AuditAction commonAction :
        com.njydsz.common.audit.enums.AuditAction.values()) {
      if (commonAction.getCode() == actionCode) {
        switch (commonAction) {
          case CREATE:
            return AuditAction.CREATE;
          case UPDATE:
            return AuditAction.UPDATE;
          case DELETE:
            return AuditAction.DELETE;
          case IMPORT:
            return AuditAction.IMPORT;
          case EXPORT:
            return AuditAction.EXPORT;
          case APPROVE:
            return AuditAction.APPROVE;
          case REJECT:
            return AuditAction.REJECT;
          case ENABLE:
          case DISABLE:
            return AuditAction.TOGGLE;
          case RESTORE:
            return AuditAction.ROLLBACK;
          default:
            return AuditAction.STATUS_CHANGE;
        }
      }
    }
    return AuditAction.STATUS_CHANGE;
  }

  /**
   * 从通用审计状态编码映射回自建审计结果枚举
   *
   * @param statusCode 通用审计状态编码
   * @return 自建审计结果枚举
   */
  private AuditResult fromCommonStatus(Integer statusCode) {
    if (statusCode == null) {
      return AuditResult.SUCCESS;
    }
    return statusCode == AuditStatus.SUCCESS.getCode() ? AuditResult.SUCCESS : AuditResult.FAILURE;
  }

  /**
   * 解析 content 字符串，提取 source 和 changeDesc
   *
   * <p>格式：{@code "[操作描述] source=XXX changeDesc"}
   *
   * @param content 内容字符串
   * @param entry 审计日志条目（会被修改 source 和 changeDesc 字段）
   */
  private void parseContent(String content, AuditLogEntry entry) {
    if (content == null) {
      return;
    }
    String remaining = content;
    // 去掉前缀 [xxx]
    int bracketEnd = remaining.indexOf(']');
    if (bracketEnd >= 0) {
      remaining = remaining.substring(bracketEnd + 1).trim();
    }
    // 提取 source=XXX
    if (remaining.startsWith("source=")) {
      int spaceIdx = remaining.indexOf(' ');
      if (spaceIdx > 0) {
        entry.setSource(remaining.substring(SOURCE_PREFIX_LENGTH, spaceIdx));
        remaining = remaining.substring(spaceIdx + 1).trim();
      } else {
        entry.setSource(remaining.substring(SOURCE_PREFIX_LENGTH));
        remaining = "";
      }
    }
    // 剩余部分为 changeDesc
    if (!remaining.isEmpty()) {
      entry.setChangeDesc(remaining);
    }
  }

  /**
   * 写入审计日志到通用审计框架
   *
   * @param entry 自建审计日志条目（用于日志输出）
   * @param action 通用审计操作
   * @param beforeSnapshot 变更前快照
   * @param afterSnapshot 变更后快照
   * @param status 审计状态（为 null 时默认 SUCCESS）
   * @param errorMessage 错误信息
   */
  private void record(
      AuditLogEntry entry,
      com.njydsz.common.audit.enums.AuditAction action,
      Map<String, Object> beforeSnapshot,
      Map<String, Object> afterSnapshot,
      AuditStatus status,
      String errorMessage) {
    try {
      if (auditRecorder == null) {
        log.info(
            "[AuditLog] (no-op) {} {} by {} from {}",
            action,
            entry.getRuleCode(),
            entry.getOperator(),
            entry.getSource());
        return;
      }

      AuditLog auditLog = new AuditLog();
      auditLog.setId(UUID.randomUUID().toString().replace("-", ""));
      auditLog.setAuditType(AuditType.OPERATION.getCode());
      auditLog.setAction(action.getCode());
      auditLog.setStatus(status != null ? status.getCode() : AuditStatus.SUCCESS.getCode());
      auditLog.setModule(MODULE_RULE_ENGINE);
      auditLog.setBusinessNo(entry.getRuleCode());
      auditLog.setOperatorId(entry.getOperator());
      auditLog.setOperatorName(entry.getOperator());
      auditLog.setOperationTime(entry.getCreatedAt());
      auditLog.setCreatedAt(entry.getCreatedAt());
      auditLog.setErrorMessage(errorMessage);

      // 构建 content
      String content = buildContent(action, entry.getSource(), entry.getChangeDesc());
      auditLog.setContent(content);

      // 序列化快照
      if (beforeSnapshot != null && !beforeSnapshot.isEmpty()) {
        auditLog.setDiffBeforeSnapshot(YdszJson.toJson(beforeSnapshot));
      }
      if (afterSnapshot != null && !afterSnapshot.isEmpty()) {
        auditLog.setDiffAfterSnapshot(YdszJson.toJson(afterSnapshot));
      }

      auditRecorder.recordAsync(auditLog);

      log.info(
          "[AuditLog] {} {} by {} from {}",
          action,
          entry.getRuleCode(),
          entry.getOperator(),
          entry.getSource());
    } catch (Exception e) {
      log.warn("[AuditLog] 审计日志记录失败: {}", e.getMessage());
    }
  }

  /**
   * 构建审计日志 content 内容
   *
   * @param action 通用审计操作
   * @param source 操作来源
   * @param changeDesc 变更描述（可为 null）
   * @return content 字符串
   */
  private String buildContent(
      com.njydsz.common.audit.enums.AuditAction action, String source, String changeDesc) {
    StringBuilder sb = new StringBuilder();
    if (action != null) {
      sb.append("[").append(action.getDescription()).append("]");
    }
    if (source != null && !source.isEmpty()) {
      sb.append(" source=").append(source);
    }
    if (changeDesc != null && !changeDesc.isEmpty()) {
      sb.append(" ").append(changeDesc);
    }
    return sb.toString().trim();
  }

  private Map<String, Object> toSnapshot(RuleDefinitionDTO def) {
    if (def == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> snapshot = new LinkedHashMap<>(COLLECTION_CAPACITY);
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
    Map<String, FieldDiff> diffs = new LinkedHashMap<>(COLLECTION_CAPACITY);
    if (oldDef == null || newDef == null) {
      return diffs;
    }

    compareField(
        diffs, "conditionExpression", oldDef.getConditionExpression(), newDef.getConditionExpression());
    compareField(
        diffs, "severityExpression", oldDef.getSeverityExpression(), newDef.getSeverityExpression());
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

  // ==================== 内部枚举与模型 ====================

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
  @NoArgsConstructor
  @AllArgsConstructor
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
}
