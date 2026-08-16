package com.njydsz.common.audit.event;

import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditStatus;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审计事件监听器
 *
 * <p>消费业务模块通过 {@code ApplicationEventPublisher} 发布的审计事件， 将 {@link OperationLogEvent} / {@link
 * DataExportAuditEvent} 转换为 统一的 {@link AuditLog} 实体并通过 {@link AuditRecorder} 异步落库。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>使用 {@link TransactionalEventListener}(phase = AFTER_COMMIT) 确保事件在业务事务提交后才消费， 避免业务回滚后审计日志残留
 *   <li>事件消费逻辑异常被隔离捕获，绝不反向影响业务主链路
 *   <li>事件携带的审计字段（userId / module / action 等）优先使用， 缺失字段使用 {@code null} 占位，避免 NPE
 *   <li>支持 {@link Async} 异步消费（需业务主类或配置类显式启用 {@code @EnableAsync}）
 * </ul>
 *
 * <h3>事件 → 审计字段映射</h3>
 *
 * <table border="1">
 *   <tr><th>OperationLogEvent 字段</th><th>AuditLog 字段</th><th>映射规则</th></tr>
 *   <tr><td>module</td><td>module</td><td>直接映射</td></tr>
 *   <tr><td>userId</td><td>operatorId</td><td>角色转换</td></tr>
 *   <tr><td>username</td><td>operatorName</td><td>角色转换</td></tr>
 *   <tr><td>action + bizType</td><td>action</td><td>action 需转换为 AuditAction 编码（字符串存储）</td></tr>
 *   <tr><td>bizId</td><td>businessNo</td><td>角色转换</td></tr>
 *   <tr><td>requestUrl</td><td>content</td><td>截断至数据库列宽</td></tr>
 *   <tr><td>clientIp</td><td>ipAddress</td><td>字段重命名</td></tr>
 *   <tr><td>paramsJson</td><td>requestParams</td><td>角色转换</td></tr>
 *   <tr><td>responseJson</td><td>responseResult</td><td>角色转换</td></tr>
 *   <tr><td>status</td><td>status</td><td>SUCCESS → 1, FAILED → 0</td></tr>
 *   <tr><td>errorMessage</td><td>errorMessage</td><td>直接映射</td></tr>
 *   <tr><td>costMs</td><td>costTime</td><td>字段重命名</td></tr>
 *   <tr><td>traceId</td><td>traceId</td><td>直接映射</td></tr>
 *   <tr><td>tenantId</td><td>tenantId</td><td>直接映射</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@RequiredArgsConstructor
public class AuditEventListener {

  private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

  /** 内容字段最大长度（与数据库 VARCHAR(512) 对齐） */
  private static final int MAX_CONTENT_LENGTH = 512;

  private final AuditRecorder auditRecorder;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 消费操作日志事件（事务提交后）
   *
   * <p>将 {@link OperationLogEvent} 转换为 {@link AuditLog} 并异步落库。 业务模块通过在代码中 {@code publishEvent(new
   * OperationLogEvent(...))} 发布事件， 即可将操作审计数据统一汇入 sys_audit_log 表。
   *
   * @param event 操作日志事件
   */
  @TransactionalEventListener(fallbackExecute = true)
  @Async("auditAsyncExecutor")
  public void onOperationLog(OperationLogEvent event) {
    if (event == null) {
      return;
    }
    try {
      AuditLog auditLog = convertFromOperationLog(event);
      auditRecorder.record(auditLog);
      log.debug(
          "[AuditEvent] 操作日志事件已消费: userId={}, module={}, action={}",
          event.getUserId(),
          event.getModule(),
          event.getAction());
    } catch (Exception e) {
      log.error("[AuditEvent] 消费操作日志事件异常: reason={}", e.getMessage(), e);
    }
  }

  /**
   * 消费数据导出审计事件（事务提交后）
   *
   * <p>导出操作的审计独立记录，通过 bizId 与导出任务关联。
   *
   * @param event 数据导出审计事件
   */
  @TransactionalEventListener(fallbackExecute = true)
  @Async("auditAsyncExecutor")
  public void onDataExport(DataExportAuditEvent event) {
    if (event == null) {
      return;
    }
    try {
      AuditLog auditLog = convertFromDataExport(event);
      auditRecorder.record(auditLog);
      log.debug(
          "[AuditEvent] 数据导出事件已消费: userId={}, module={}, rowCount={}",
          event.getUserId(),
          event.getExportModule(),
          event.getRowCount());
    } catch (Exception e) {
      log.error("[AuditEvent] 消费数据导出事件异常: reason={}", e.getMessage(), e);
    }
  }

  /**
   * 将 OperationLogEvent 转换为 AuditLog 实体
   *
   * @param event 操作日志事件
   * @return 审计日志实体
   */
  private AuditLog convertFromOperationLog(OperationLogEvent event) {
    AuditLog auditLog = new AuditLog();

    // 基础标识
    auditLog.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    auditLog.setOperationTime(LocalDateTime.now());
    auditLog.setCreatedAt(LocalDateTime.now());

    // 操作人信息
    auditLog.setOperatorId(event.getUserId());
    auditLog.setOperatorName(event.getUsername());

    // 模块与内容
    auditLog.setModule(event.getModule());
    auditLog.setContent(truncate(event.getRequestUrl(), MAX_CONTENT_LENGTH));

    // 审计类型与行为（默认 OPERATION，action 存储语义字符串）
    auditLog.setAuditType(AuditType.OPERATION.getCode());
    auditLog.setAction(resolveActionCode(event.getAction()));

    // 业务关联
    auditLog.setBusinessNo(event.getBizId());

    // 请求上下文
    auditLog.setIpAddress(event.getClientIp());
    auditLog.setRequestParams(event.getParamsJson());
    auditLog.setResponseResult(event.getResponseJson());

    // 状态映射：SUCCESS → 1, FAILED/其他 → 0
    auditLog.setStatus(
        isSuccess(event.getStatus())
            ? AuditStatus.SUCCESS.getCode()
            : AuditStatus.FAILURE.getCode());

    auditLog.setErrorMessage(event.getErrorMessage());
    auditLog.setCostTime(event.getCostMs());
    auditLog.setTraceId(event.getTraceId());
    auditLog.setTenantId(event.getTenantId());

    return auditLog;
  }

  /**
   * 将 DataExportAuditEvent 转换为 AuditLog 实体
   *
   * @param event 数据导出审计事件
   * @return 审计日志实体
   */
  private AuditLog convertFromDataExport(DataExportAuditEvent event) {
    AuditLog auditLog = new AuditLog();

    auditLog.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    auditLog.setOperationTime(LocalDateTime.now());
    auditLog.setCreatedAt(LocalDateTime.now());

    auditLog.setOperatorId(event.getUserId());
    auditLog.setOperatorName(event.getUsername());

    auditLog.setModule(event.getExportModule());
    auditLog.setContent(
        (event.getRowCount() != null)
            ? "导出[" + event.getExportModule() + "]共" + event.getRowCount() + "行数据"
            : "导出[" + event.getExportModule() + "]");

    auditLog.setAuditType(AuditType.OPERATION.getCode());
    auditLog.setAction(AuditAction.EXPORT.getCode());
    auditLog.setBusinessNo(event.getBizId());

    auditLog.setIpAddress(event.getClientIp());
    auditLog.setStatus(AuditStatus.SUCCESS.getCode());
    auditLog.setTraceId(event.getTraceId());
    auditLog.setTenantId(event.getTenantId());

    return auditLog;
  }

  /**
   * 解析操作行为编码（将语义字符串转为 AuditAction 编码）
   *
   * <p>若 action 字符串可匹配已知枚举则返回对应编码， 否则使用原字符串的 hashCode 取正数作为兜底编码。
   *
   * @param action 操作行为语义字符串
   * @return 审计行为编码
   */
  private Integer resolveActionCode(String action) {
    if (action == null || action.isEmpty()) {
      return AuditAction.OTHER.getCode();
    }
    for (AuditAction auditAction : AuditAction.values()) {
      if (auditAction.name().equalsIgnoreCase(action)
          || String.valueOf(auditAction.getCode()).equals(action)) {
        return auditAction.getCode();
      }
    }
    // 兜底：未知 action 使用正 hashCode 作为自定义编码
    return Math.abs(action.hashCode()) % 10000 + 1000;
  }

  /**
   * 判断操作状态是否成功
   *
   * @param status 状态字符串
   * @return 成功返回 true
   */
  private boolean isSuccess(String status) {
    return "SUCCESS".equalsIgnoreCase(status);
  }

  /**
   * 截断超长内容
   *
   * @param content 原始内容
   * @param maxLength 最大长度
   * @return 截断后的内容
   */
  private String truncate(String content, int maxLength) {
    if (content == null) {
      return null;
    }
    if (content.length() <= maxLength) {
      return content;
    }
    return content.substring(0, maxLength);
  }
}
