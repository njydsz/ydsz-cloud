package com.njydsz.workflow.server.service.impl.integration;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.EmbeddedApprovalActionDTO;
import com.njydsz.workflow.domain.entity.FlowThirdPartyAccountDO;
import com.njydsz.workflow.domain.entity.FlowThirdPartyLogDO;
import com.njydsz.workflow.infra.mapper.FlowThirdPartyLogMapper;
import com.njydsz.workflow.server.service.FlowEmbeddedApprovalService;
import com.njydsz.workflow.server.service.FlowThirdPartyAccountService;
import com.njydsz.workflow.server.service.FlowThirdPartyLogService;
import com.njydsz.workflow.server.service.FlowThirdPartyRetryService;
import com.njydsz.workflow.server.thirdparty.ThirdPartyApprovalActionResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 三方审批回调重试服务实现
 *
 * <p>P0-4: 失败回调最终一致性保证。
 *
 * <p>重试逻辑与 {@code FlowThirdPartyApprovalController.dispatchApprovalAction} 保持一致：
 * <ol>
 *   <li>从 callbackData 反序列化为 Map</li>
 *   <li>解析三方事件 → 工作流动作（PASS/REJECT/WITHDRAW）</li>
 *   <li>通过 openId 反查系统用户</li>
 *   <li>调用 {@link FlowEmbeddedApprovalService#quickAction} 派发</li>
 * </ol>
 *
 * <p>重试时不校验签名（首次回调已校验过），不写新的回调日志（更新原日志状态即可）。
 *
 * <p>事务边界设计：
 * <ul>
 *   <li>{@link #retryOneInternal} 使用 REQUIRES_NEW 子事务，仅承载 quickAction 等业务调用，
 *       不调用 mapper.updateRetryResult（避免子事务回滚污染状态更新）。</li>
 *   <li>状态更新（SUCCESS/FAIL + retryCount++）统一在外层 {@link #retryBatch} / {@link #retryOne}
 *       中通过 {@link #updateRetryResultSafely} 调用，无事务包裹，每次更新独立提交。</li>
 * </ul>
 *
 * <p>容错策略与首次处理对齐：
 * <ul>
 *   <li>事件不支持 / 缺 businessType → 标 SUCCESS（与首次一致，避免死循环）</li>
 *   <li>账号未映射 → 标 FAIL（账号映射可能在后续补全，可继续重试）</li>
 *   <li>SysException（找不到任务/流程已结束）→ 标 SUCCESS（容错跳过，与首次一致）</li>
 *   <li>其他系统异常 → 标 FAIL（继续重试直至超阈值进入死信）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartyRetryServiceImpl implements FlowThirdPartyRetryService {

    /** 三方对接日志 Mapper */
    private final FlowThirdPartyLogMapper thirdPartyLogMapper;
    /** 三方账号映射服务 */
    private final FlowThirdPartyAccountService thirdPartyAccountService;
    /** 嵌入式审批服务（驱动工作流通过/驳回/撤回） */
    private final FlowEmbeddedApprovalService embeddedApprovalService;

    @Override
    public RetryResult retryBatch(int maxRetries, int batchSize) {
        RetryResult result = new RetryResult();
        if (maxRetries <= 0 || batchSize <= 0) {
            return result;
        }

        List<FlowThirdPartyLogDO> failedList;
        try {
            failedList = thirdPartyLogMapper.selectFailedForRetry(maxRetries, batchSize);
        } catch (Exception e) {
            log.error("[ThirdPartyRetry] 扫描失败回调异常: maxRetries={} batchSize={} err={}",
                    maxRetries, batchSize, e.getMessage(), e);
            result.errors = 1;
            return result;
        }

        if (failedList == null || failedList.isEmpty()) {
            return result;
        }

        result.scanned = failedList.size();
        for (FlowThirdPartyLogDO logEntry : failedList) {
            int newRetryCount = nextRetryCount(logEntry);
            try {
                RetryOutcome outcome = retryOneInternal(logEntry);
                switch (outcome) {
                    case SUCCESS -> {
                        result.success++;
                        updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_SUCCESS,
                                null, newRetryCount);
                    }
                    case FAIL_ACCOUNT_NOT_MAPPED -> {
                        result.fail++;
                        updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_FAIL,
                                "account not mapped", newRetryCount);
                        log.warn("[ThirdPartyRetry] 账号未映射 logId={} platform={} openId={} retryCount={}/{}",
                                logEntry.getId(), logEntry.getPlatform(),
                                extractOpenId(logEntry), newRetryCount, maxRetries);
                    }
                    case SKIPPED -> {
                        result.skipped++;
                        updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_SUCCESS,
                                null, newRetryCount);
                        log.info("[ThirdPartyRetry] 容错跳过 logId={} platform={} eventType={}",
                                logEntry.getId(), logEntry.getPlatform(), logEntry.getEventType());
                    }
                }
            } catch (Exception e) {
                result.errors++;
                updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_FAIL,
                        truncate(e.getMessage(), 512), newRetryCount);
                log.error("[ThirdPartyRetry] 重试单条异常 logId={} platform={} err={}",
                        logEntry.getId(), logEntry.getPlatform(), e.getMessage(), e);
            }
        }

        log.info("[ThirdPartyRetry] 重试批次完成: scanned={} success={} fail={} skipped={} errors={} maxRetries={}",
                result.scanned, result.success, result.fail, result.skipped, result.errors, maxRetries);
        return result;
    }

    @Override
    public boolean retryOne(String logId) {
        if (logId == null || logId.isBlank()) {
            return false;
        }
        FlowThirdPartyLogDO logEntry;
        try {
            logEntry = thirdPartyLogMapper.selectById(logId);
        } catch (Exception e) {
            log.error("[ThirdPartyRetry] 查询日志失败 logId={} err={}", logId, e.getMessage(), e);
            return false;
        }
        if (logEntry == null) {
            log.warn("[ThirdPartyRetry] 日志不存在，无法重试: logId={}", logId);
            return false;
        }
        return retryOne(logEntry);
    }

    @Override
    public boolean retryOne(FlowThirdPartyLogDO logEntry) {
        if (logEntry == null) {
            return false;
        }
        int newRetryCount = nextRetryCount(logEntry);
        try {
            RetryOutcome outcome = retryOneInternal(logEntry);
            return switch (outcome) {
                case SUCCESS, SKIPPED -> {
                    updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_SUCCESS,
                            null, newRetryCount);
                    log.info("[ThirdPartyRetry] 手动重试成功 logId={} outcome={} retryCount={}",
                            logEntry.getId(), outcome, newRetryCount);
                    yield true;
                }
                case FAIL_ACCOUNT_NOT_MAPPED -> {
                    updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_FAIL,
                            "account not mapped", newRetryCount);
                    log.info("[ThirdPartyRetry] 手动重试失败(账号未映射) logId={} retryCount={}",
                            logEntry.getId(), newRetryCount);
                    yield false;
                }
            };
        } catch (Exception e) {
            updateRetryResultSafely(logEntry, FlowThirdPartyLogService.STATUS_FAIL,
                    truncate(e.getMessage(), 512), newRetryCount);
            log.error("[ThirdPartyRetry] 手动重试异常 logId={} err={}",
                    logEntry.getId(), e.getMessage(), e);
            return false;
        }
    }

    // ============================== 内部方法 ==============================

    /**
     * 重试单条日志的核心逻辑（REQUIRES_NEW 子事务，仅承载业务调用，不更新日志状态）
     *
     * <p>事务边界：本方法仅包裹 {@link FlowEmbeddedApprovalService#quickAction} 等业务调用，
     * 不调用 {@code mapper.updateRetryResult}。这样即使 quickAction 抛出 RuntimeException 导致
     * 子事务回滚，也不会污染外层的状态更新逻辑。
     *
     * @param logEntry 日志记录
     * @return 重试结果枚举
     * @throws Exception 系统异常（DB/网络等），由上层捕获并标记 FAIL
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    protected RetryOutcome retryOneInternal(FlowThirdPartyLogDO logEntry) {
        // 1. 反序列化 callbackData
        Map<String, Object> body = parseCallbackData(logEntry.getCallbackData());
        if (body == null) {
            // callbackData 损坏 → 容错跳过标 SUCCESS（数据问题需运维介入）
            return RetryOutcome.SKIPPED;
        }

        // 2. 解析事件 → 工作流动作
        String platform = logEntry.getPlatform();
        String eventType = logEntry.getEventType();
        ThirdPartyApprovalActionResolver.FlowAction action =
                ThirdPartyApprovalActionResolver.resolve(platform, eventType, body);
        if (action == null) {
            return RetryOutcome.SKIPPED;
        }

        // 3. 读取业务类型/业务 ID
        String businessType = mapStr(body, "businessType");
        String businessId = mapStr(body, "businessId");
        if (businessType == null || businessType.isBlank()
                || businessId == null || businessId.isBlank()) {
            return RetryOutcome.SKIPPED;
        }

        // 4. 通过 openId 反查系统用户
        String openId = mapStr(body, "openId");
        FlowThirdPartyAccountDO account = null;
        if (openId != null) {
            account = thirdPartyAccountService.getByOpenId(platform, openId);
        }
        if (account == null) {
            // 账号未映射 → 标 FAIL（账号映射可能后续补全，可继续重试）
            return RetryOutcome.FAIL_ACCOUNT_NOT_MAPPED;
        }

        // 5. 派发审批动作
        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        dto.setAction(action.code());
        dto.setUserId(account.getUserId());
        dto.setComment(mapStr(body, "comment"));
        if (account.getTenantId() != null) {
            dto.setTenantId(account.getTenantId());
        }

        try {
            embeddedApprovalService.quickAction(dto);
        } catch (SysException e) {
            // 业务异常（找不到任务/流程已结束）— 与首次处理一致，容错跳过标 SUCCESS
            log.warn("[ThirdPartyRetry] SysException 容错跳过 logId={} code={} msg={}",
                    logEntry.getId(), e.getCode(), e.getMessage());
            return RetryOutcome.SKIPPED;
        }

        log.info("[ThirdPartyRetry] 重试成功 logId={} platform={} userId={} action={}",
                logEntry.getId(), platform, account.getUserId(), action);
        return RetryOutcome.SUCCESS;
    }

    /**
     * 计算下一次重试次数
     */
    private int nextRetryCount(FlowThirdPartyLogDO logEntry) {
        Integer current = logEntry.getRetryCount();
        return (current == null ? 0 : current) + 1;
    }

    /**
     * 安全地更新重试结果（不抛异常，避免状态更新失败拖垮主流程）
     */
    private void updateRetryResultSafely(FlowThirdPartyLogDO logEntry, String status,
                                         String errorMsg, int retryCount) {
        try {
            thirdPartyLogMapper.updateRetryResult(logEntry.getId(), status, errorMsg, retryCount);
        } catch (Exception e) {
            log.error("[ThirdPartyRetry] 更新重试结果失败 logId={} status={} retryCount={} err={}",
                    logEntry.getId(), status, retryCount, e.getMessage(), e);
        }
    }

    /**
     * 反序列化 callbackData 为 Map
     *
     * @return Map；null 或解析失败返回 null
     */
    private Map<String, Object> parseCallbackData(String callbackData) {
        if (callbackData == null || callbackData.isBlank()) {
            return null;
        }
        try {
            return YdszJson.parseMap(callbackData);
        } catch (Exception e) {
            log.warn("[ThirdPartyRetry] callbackData 解析失败: {} err={}",
                    callbackData.length() > 200 ? callbackData.substring(0, 200) + "..." : callbackData,
                    e.getMessage());
            return null;
        }
    }

    /**
     * 从 Map 中安全提取字符串值
     */
    private String mapStr(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 从日志的 callbackData 中提取 openId（用于日志输出）
     */
    private String extractOpenId(FlowThirdPartyLogDO logEntry) {
        Map<String, Object> body = parseCallbackData(logEntry.getCallbackData());
        return body == null ? null : mapStr(body, "openId");
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    /**
     * 重试结果枚举（内部使用，区分不同失败原因以便上层决定状态标记策略）
     */
    private enum RetryOutcome {
        /** 重试成功 */
        SUCCESS,
        /** 账号未映射 → 标 FAIL（可继续重试） */
        FAIL_ACCOUNT_NOT_MAPPED,
        /** 容错跳过 → 标 SUCCESS（事件不支持/缺参数/SysException，避免死循环） */
        SKIPPED
    }
}
