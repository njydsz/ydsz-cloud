package com.njydsz.workflow.server.service;

import com.njydsz.workflow.domain.entity.FlowThirdPartyLogDO;

/**
 * 三方审批回调重试服务
 *
 * <p>P0-4: 失败回调的最终一致性保证。
 *
 * <p>回调处理失败（如账号未映射、工作流分发异常等）时，日志落库为 FAIL 状态。
 * 本服务负责：
 * <ul>
 *   <li>扫描 FAIL 状态且 retry_count 未超阈值的日志</li>
 *   <li>重新解析 callbackData、反查账号、派发审批动作</li>
 *   <li>更新重试结果（SUCCESS / FAIL + retryCount++）</li>
 *   <li>超过最大重试次数的进入死信（不再扫描，等运维介入）</li>
 * </ul>
 *
 * <p>容错策略：
 * <ul>
 *   <li>单条重试失败不影响其余，每条独立 try-catch</li>
 *   <li>账号仍未映射 → 直接判 FAIL 但不抛异常（与首次处理一致）</li>
 *   <li>工作流业务异常（SysException）→ 容错跳过，标记为 FAIL（避免死循环重试已结束的流程）</li>
 *   <li>系统异常（数据库/网络） → 抛出，由调用方决定后续策略</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowThirdPartyRetryService {

    /**
     * 扫描并重试一批失败回调
     *
     * <p>由 {@code FlowThirdPartyRetryJobHandler} 周期性调用。
     *
     * @param maxRetries 最大重试次数阈值（retry_count &lt; maxRetries 才会被扫描）
     * @param batchSize  单批最多处理条数
     * @return 处理结果摘要：scanned / success / fail / skipped / errors
     */
    RetryResult retryBatch(int maxRetries, int batchSize);

    /**
     * 手动重试单条日志（预留，供运维后台或 API 调用）
     *
     * <p>不受 maxRetries 阈值限制，强制重试一次。
     *
     * @param logId 日志 ID
     * @return true 表示重试成功（handle_status=SUCCESS）；false 表示重试失败
     */
    boolean retryOne(String logId);

    /**
     * 重试单条日志（内部复用，直接传 DO 避免二次查询）
     *
     * @param logEntry 日志记录
     * @return true 表示重试成功
     */
    boolean retryOne(FlowThirdPartyLogDO logEntry);

    /**
     * 重试结果摘要
     */
    class RetryResult {
        /** 本批扫描条数 */
        public int scanned;
        /** 重试成功条数 */
        public int success;
        /** 重试失败条数 */
        public int fail;
        /** 跳过条数（账号未映射 / 事件不支持等） */
        public int skipped;
        /** 系统异常条数 */
        public int errors;

        public RetryResult() {
        }

        public RetryResult(int scanned, int success, int fail, int skipped, int errors) {
            this.scanned = scanned;
            this.success = success;
            this.fail = fail;
            this.skipped = skipped;
            this.errors = errors;
        }
    }
}
