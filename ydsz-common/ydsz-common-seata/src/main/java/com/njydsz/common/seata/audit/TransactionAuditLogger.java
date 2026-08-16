package com.njydsz.common.seata.audit;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.seata.api.TransactionType;

/**
 * 分布式事务审计日志
 *
 * <p>以结构化 JSON 格式记录每次分布式事务的审计信息：
 *
 * <ul>
 *   <li>操作时间
 *   <li>事务名称
 *   <li>事务类型
 *   <li>XID / branchId
 *   <li>traceId（P1-7 新增：自动从 MDC 或 X-Trace-Id 提取）
 *   <li>执行结果（success/fail）
 *   <li>耗时（毫秒）
 *   <li>异常信息（如有）
 * </ul>
 *
 * <p>日志输出到独立的 audit logger，可由 Loki/ELK 采集。
 *
 * <p><b>P1-7 新增</b>：traceId 自动注入，支持与分布式链路系统（如 MDD/SkyWalking）关联。 注入优先级：
 *
 * <ol>
 *   <li>SLF4J MDC 中的 {@code traceId} 或 {@code trace_id}
 *   <li>HTTP 请求头 {@code X-Trace-Id}（需前置拦截器写入 MDC）
 * </ol>
 *
 * <h3>MDC 配置建议</h3>
 *
 * 推荐在 Web 拦截器或 Servlet Filter 中提取 traceId 并放入 MDC：
 *
 * <pre>{@code
 * String traceId = request.getHeader("X-Trace-Id");
 * if (traceId != null) {
 *     MDC.put("traceId", traceId);
 * }
 * }</pre>
 *
 * <h3>Logback 配置建议</h3>
 *
 * 在 logback-spring.xml 中修改 pattern 输出 traceId：
 *
 * <pre>{@code
 * %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{36} - %msg%n
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TransactionAuditLogger {

  private static final Logger auditLog = LoggerFactory.getLogger("SEATA_AUDIT");

  /** MDC 中 traceId 的备选键名 */
  private static final String[] TRACE_ID_KEYS = {"traceId", "trace_id", "X-Trace-Id"};

  /**
   * 记录事务审计日志
   *
   * @param transactionName 事务名称
   * @param type 事务类型
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID（可为 null）
   * @param result 执行结果
   * @param durationMs 耗时
   * @param error 异常信息（可为 null）
   */
  public void audit(
      String transactionName,
      TransactionType type,
      String xid,
      String branchId,
      String result,
      long durationMs,
      String error) {
    if (!auditLog.isInfoEnabled()) {
      return;
    }
    Map<String, Object> audit = new LinkedHashMap<>();
    audit.put("timestamp", LocalDateTime.now().toString());
    audit.put("txName", transactionName);
    audit.put("type", type.name());
    audit.put("xid", xid);
    if (branchId != null) {
      audit.put("branchId", branchId);
    }

    // P1-7: traceId 注入
    String traceId = resolveTraceId();
    if (traceId != null) {
      audit.put("traceId", traceId);
    }

    audit.put("result", result);
    audit.put("durationMs", durationMs);
    if (error != null) {
      audit.put("error", error);
    }
    auditLog.info(YdszJson.toJson(audit));
  }

  /**
   * 记录事务开始审计
   *
   * @param transactionName 事务名称
   * @param type 事务类型
   * @param xid 全局事务 ID
   */
  public void auditStart(String transactionName, TransactionType type, String xid) {
    audit(transactionName, type, xid, null, "started", 0, null);
  }

  /**
   * 记录事务成功审计
   *
   * @param transactionName 事务名称
   * @param type 事务类型
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID（可为 null）
   * @param durationMs 耗时（毫秒）
   */
  public void auditSuccess(
      String transactionName, TransactionType type, String xid, String branchId, long durationMs) {
    audit(transactionName, type, xid, branchId, "success", durationMs, null);
  }

  /**
   * 记录事务失败审计
   *
   * @param transactionName 事务名称
   * @param type 事务类型
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID（可为 null）
   * @param durationMs 耗时（毫秒）
   * @param error 错误信息
   */
  public void auditFailure(
      String transactionName,
      TransactionType type,
      String xid,
      String branchId,
      long durationMs,
      String error) {
    audit(transactionName, type, xid, branchId, "fail", durationMs, error);
  }

  /**
   * 解析当前线程的链路追踪 ID
   *
   * <p>依次从 MDC 中查找 {@code traceId}、{@code trace_id}、{@code X-Trace-Id}， 找到任一非空值即返回；全部无值时返回 null。
   *
   * @return traceId 或 null
   */
  protected String resolveTraceId() {
    for (String key : TRACE_ID_KEYS) {
      String traceId = MDC.get(key);
      if (traceId != null && !traceId.isBlank()) {
        return traceId;
      }
    }
    return null;
  }
}
