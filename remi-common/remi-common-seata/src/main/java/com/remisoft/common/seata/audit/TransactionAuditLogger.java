package com.remisoft.common.seata.audit;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.json.YdszJson;
import com.remisoft.common.seata.api.TransactionType;

/**
 * 分布式事务审计日志
 *
 * <p>以结构化 JSON 格式记录每次分布式事务的审计信息：
 * <ul>
 *   <li>操作时间</li>
 *   <li>事务名称</li>
 *   <li>事务类型</li>
 *   <li>XID / branchId</li>
 *   <li>执行结果（success/fail）</li>
 *   <li>耗时（毫秒）</li>
 *   <li>异常信息（如有）</li>
 * </ul>
 *
 * <p>日志输出到独立的 audit logger，可由 Loki/ELK 采集。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class TransactionAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("SEATA_AUDIT");

    /**
     * 记录事务审计日志
     *
     * @param transactionName 事务名称
     * @param type           事务类型
     * @param xid            全局事务 ID
     * @param branchId       分支事务 ID（可为 null）
     * @param result         执行结果
     * @param durationMs     耗时
     * @param error          异常信息（可为 null）
     */
    public void audit(String transactionName, TransactionType type, String xid,
                      String branchId, String result, long durationMs, String error) {
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
        audit.put("result", result);
        audit.put("durationMs", durationMs);
        if (error != null) {
            audit.put("error", error);
        }
        auditLog.info(YdszJson.toJson(audit));
    }

    /**
     * 记录事务开始审计
     */
    public void auditStart(String transactionName, TransactionType type, String xid) {
        audit(transactionName, type, xid, null, "started", 0, null);
    }

    /**
     * 记录事务成功审计
     */
    public void auditSuccess(String transactionName, TransactionType type, String xid,
                             String branchId, long durationMs) {
        audit(transactionName, type, xid, branchId, "success", durationMs, null);
    }

    /**
     * 记录事务失败审计
     */
    public void auditFailure(String transactionName, TransactionType type, String xid,
                             String branchId, long durationMs, String error) {
        audit(transactionName, type, xid, branchId, "fail", durationMs, error);
    }
}
