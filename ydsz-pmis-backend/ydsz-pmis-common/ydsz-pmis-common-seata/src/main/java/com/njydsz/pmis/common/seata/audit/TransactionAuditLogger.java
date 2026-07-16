package com.njydsz.pmis.common.seata.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.seata.api.TransactionType;

import java.time.LocalDateTime;

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
 * @author ydsz-pmis-team
 * @since 3.5.0
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
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"timestamp\":\"").append(LocalDateTime.now()).append("\"");
        sb.append(",\"txName\":\"").append(transactionName).append("\"");
        sb.append(",\"type\":\"").append(type).append("\"");
        sb.append(",\"xid\":\"").append(xid).append("\"");
        if (branchId != null) {
            sb.append(",\"branchId\":\"").append(branchId).append("\"");
        }
        sb.append(",\"result\":\"").append(result).append("\"");
        sb.append(",\"durationMs\":").append(durationMs);
        if (error != null) {
            sb.append(",\"error\":\"").append(error.replace("\"", "\\\"")).append("\"");
        }
        sb.append("}");
        auditLog.info(sb.toString());
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
