package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.cronjob.api.client.CronjobServiceClient;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 定时任务触发动作处理器（P1-2 规则与定时任务联动）
 *
 * <p>当规则触发时，自动触发关联的 cronjob 定时任务。
 * 任务 ID 来源（按优先级）：
 * <ol>
 *   <li>{@code RuleResult} 的 {@code scope} 字段（格式: "cronjob:{jobId}"）</li>
 *   <li>{@code RuleContext} facts 中的 {@code cronjobJobId} 键</li>
 *   <li>{@code RuleContext} facts 中的 {@code cronjob.jobId} 键（嵌套 Map）</li>
 * </ol>
 *
 * <h3>联动链路</h3>
 * <pre>
 * RuleEngine.evaluate
 *   → CronjobTriggerActionHandler.onTriggered
 *     → CronjobServiceClient Feign → cronjob 模块
 *       → JobService.trigger(jobId) → 立即执行一次定时任务
 * </pre>
 *
 * <h3>使用条件</h3>
 * <ul>
 *   <li>classpath 中存在 {@code CronjobServiceClient}（由 ydsz-pmis-cronjob-api 提供）</li>
 *   <li>规则结果中包含有效的 cronjob 任务 ID</li>
 *   <li>未配置任务 ID 时静默跳过，不报错</li>
 * </ul>
 *
 * <p>使用 {@code ObjectProvider} 安全注入，当 cronjob-api 不在 classpath 时不装配，
 * 不影响规则引擎核心功能。
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
public class CronjobTriggerActionHandler implements RuleActionHandler {

    private final CronjobServiceClient cronjobServiceClient;

    public CronjobTriggerActionHandler(CronjobServiceClient cronjobServiceClient) {
        this.cronjobServiceClient = cronjobServiceClient;
    }

    @Override
    public void onTriggered(List<RuleResult> results, RuleContext context) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (RuleResult result : results) {
            if (!result.isTriggered()) {
                continue;
            }
            String jobId = resolveJobId(result, context);
            if (jobId == null || jobId.isBlank()) {
                continue;
            }
            try {
                Result<String> triggerResult = cronjobServiceClient.trigger(jobId);
                if (triggerResult != null && triggerResult.isSuccess() && triggerResult.getData() != null) {
                    log.info("[LiteRule-Cronjob] 定时任务已触发: ruleCode={}, jobId={}, logId={}",
                            result.getRuleCode(), jobId, triggerResult.getData());
                } else {
                    log.warn("[LiteRule-Cronjob] 定时任务触发失败: ruleCode={}, jobId={}, result={}",
                            result.getRuleCode(), jobId, triggerResult == null ? "null" : triggerResult.getCode());
                }
            } catch (Exception e) {
                log.warn("[LiteRule-Cronjob] 定时任务触发异常: ruleCode={}, jobId={}, error={}",
                        result.getRuleCode(), jobId, e.getMessage());
            }
        }
    }

    @Override
    public String getHandlerId() {
        return "cronjob-trigger";
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * 从规则结果或上下文中解析 cronjob 任务 ID
     *
     * <p>解析优先级：
     * <ol>
     *   <li>RuleResult.scope 字段，格式为 "cronjob:{jobId}"</li>
     *   <li>RuleContext facts 中的 "cronjobJobId" 键</li>
     *   <li>RuleContext facts 中的 "cronjob" 嵌套 Map 的 "jobId" 键</li>
     * </ol>
     *
     * @param result  规则结果
     * @param context 规则上下文
     * @return 任务 ID；未找到返回 null
     */
    @SuppressWarnings("unchecked")
    private String resolveJobId(RuleResult result, RuleContext context) {
        // 1. 从 scope 字段解析 "cronjob:{jobId}"
        String scope = result.getScope();
        if (scope != null && scope.startsWith("cronjob:")) {
            String jobId = scope.substring("cronjob:".length()).trim();
            if (!jobId.isEmpty()) {
                return jobId;
            }
        }

        // 2. 从 facts 中直接获取 "cronjobJobId"
        Object directJobId = context.get("cronjobJobId");
        if (directJobId != null && !directJobId.toString().isBlank()) {
            return directJobId.toString().trim();
        }

        // 3. 从 facts 中的嵌套 "cronjob" Map 获取 "jobId"
        Object cronjobConfig = context.get("cronjob");
        if (cronjobConfig instanceof Map) {
            Object nestedJobId = ((Map<String, Object>) cronjobConfig).get("jobId");
            if (nestedJobId != null && !nestedJobId.toString().isBlank()) {
                return nestedJobId.toString().trim();
            }
        }

        return null;
    }
}
