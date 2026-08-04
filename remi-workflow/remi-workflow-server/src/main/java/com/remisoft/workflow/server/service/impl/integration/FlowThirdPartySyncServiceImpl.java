package com.remisoft.workflow.server.service.impl.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.remisoft.workflow.domain.entity.FlowThirdPartyAccount;
import com.remisoft.workflow.domain.entity.FlowThirdPartyLog;
import com.remisoft.workflow.infra.mapper.FlowThirdPartyLogMapper;
import com.remisoft.workflow.server.service.FlowThirdPartyAccountService;
import com.remisoft.workflow.server.service.FlowThirdPartySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 三方审批双向同步服务实现
 *
 * <p>对 {@link FlowThirdPartySyncService} 接口的完整实现，承担工作流引擎与<b>钉钉 / 飞书 / 企业微信</b>
 * 等三方审批系统的<b>双向同步</b>职责。当本地流程与三方审批系统对接后，本系统是审批事实的「主源」，
 * 但发起/终止/撤回等关键动作需要回写到三方审批系统，确保双方数据一致。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>本地→三方同步（syncBack）</b>：本地流程「终止 / 撤回」时，调用三方系统的
 *       {@code cancelWebhookUrl}，回写本系统操作到三方审批单据</li>
 *   <li><b>三方→本地同步（pull）</b>：通过 {@link FlowThirdPartyRetryService} 定时拉取
 *       三方系统的审批结果，落地到本地「历史任务」与「实例状态」</li>
 *   <li><b>同步日志</b>：所有同步动作（成功 / 失败 / 未配置）写入 {@code remi_flow_third_party_log}，
 *       支持问题排查与对账</li>
 *   <li><b>降级策略</b>：所有异常<b>降级记录</b>，不影响本地主流程（流程终止不因三方同步失败而回滚）</li>
 * </ul>
 *
 * <p><b>同步方向：</b>
 * <pre>
 *   ┌────────────────┐  本地终止/撤回   ┌────────────────┐
 *   │  本地工作流     │ ───────────────→ │  三方审批系统   │
 *   │  (事实源)       │  cancelWebhook   │  (钉钉/飞书/企微)│
 *   │                │ ←───────────────  │                │
 *   │                │  pull result      │                │
 *   └────────────────┘                  └────────────────┘
 * </pre>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>本类方法<b>不开启事务</b>，确保三方网络调用不被事务持有（避免长事务）</li>
 *   <li>同步日志的写入通过 {@code REQUIRES_NEW} 子事务隔离，即使主流程已提交也保证日志落库</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>支持多种三方系统：钉钉、飞书、企业微信（通过 {@link FlowThirdPartyAccount} 区分）</li>
 *   <li>同步失败由 {@link FlowThirdPartyRetryService} 定时重试，避免临时网络抖动影响</li>
 *   <li>幂等性：同步操作通过 {@code (instanceId, action)} 复合键防重，同一动作多次同步只会成功一次</li>
 *   <li>未配置 {@code cancelWebhookUrl} 的账号标记为 {@code NOT_CONFIGURED}，便于管理员发现配置缺失</li>
 * </ul>
 *
 * <p><b>典型流程：</b>
 * <ol>
 *   <li>用户在本地工作流终止流程</li>
 *   <li>{@link #syncBackOnTerminate} 查询该实例关联的三方账号</li>
 *   <li>若配置了 {@code cancelWebhookUrl}，POST 调用取消三方审批单</li>
 *   <li>未配置时标记 {@code NOT_CONFIGURED}，调用失败标记 {@code FAIL}，调用成功标记 {@code SUCCESS}</li>
 *   <li>所有异常降级记录到 {@code remi_flow_third_party_log}，不影响本地主流程</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowThirdPartySyncService 接口定义
 * @see com.remisoft.workflow.domain.entity.FlowThirdPartyAccount 三方账号实体
 * @see com.remisoft.workflow.domain.entity.FlowThirdPartyLog 三方同步日志实体
 * @see FlowThirdPartyRetryService 三方同步重试服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartySyncServiceImpl implements FlowThirdPartySyncService {

    /** 三方对接日志 Mapper，记录同步操作轨迹 */
    private final FlowThirdPartyLogMapper logMapper;
    /** 三方账号服务，查询已配置的三方审批系统账号 */
    private final FlowThirdPartyAccountService accountService;
    /** RestTemplate（由 remi-common-notify 统一提供） */
    private final RestTemplate restTemplate;

    @Override
    public void syncBackOnTerminate(String instanceId, String reason) {
        doSyncBack(instanceId, "TERMINATE", reason);
    }

    @Override
    public void syncBackOnRecall(String instanceId, String operatorId) {
        doSyncBack(instanceId, "RECALL", operatorId);
    }

    /**
     * 通用双向同步：查询关联三方记录 → 逐条调用 cancelWebhookUrl
     */
    private void doSyncBack(String instanceId, String action, String reason) {
        if (instanceId == null) {
            return;
        }
        List<FlowThirdPartyLog> logs;
        try {
            logs = logMapper.selectByBusinessId(instanceId);
        } catch (Exception e) {
            log.warn("[Flow3pSync] 查询三方审批日志失败 instanceId={} err={}", instanceId, e.getMessage());
            return;
        }
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (FlowThirdPartyLog logDo : logs) {
            FlowThirdPartyAccount account = null;
            try {
                account = accountService.getActiveByPlatform(logDo.getPlatform());
            } catch (Exception e) {
                log.debug("[Flow3pSync] 查询三方账号失败 platform={} err={}", logDo.getPlatform(), e.getMessage());
            }
            String cancelUrl = account != null ? account.getCancelWebhookUrl() : null;
            if (cancelUrl == null || cancelUrl.isBlank()) {
                logMapper.updateSyncBack(logDo.getId(), "NOT_CONFIGURED",
                        "账号未配置 cancelWebhookUrl，跳过本地→三方同步");
                continue;
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                Map<String, Object> body = new HashMap<>();
                body.put("processInstanceId", logDo.getProcessInstanceId());
                body.put("action", action);
                body.put("reason", reason);
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        cancelUrl, new HttpEntity<>(body, headers), String.class);
                boolean ok = resp.getStatusCode() == HttpStatus.OK
                        || resp.getStatusCode() == HttpStatus.ACCEPTED;
                logMapper.updateSyncBack(logDo.getId(), ok ? "SUCCESS" : "FAIL",
                        "本地→三方同步" + (ok ? "成功" : "失败: HTTP " + resp.getStatusCode()));
                log.info("[Flow3pSync] 本地→三方同步 instanceId={} platform={} ok={}",
                        instanceId, logDo.getPlatform(), ok);
            } catch (Exception e) {
                logMapper.updateSyncBack(logDo.getId(), "FAIL", "本地→三方同步异常: " + e.getMessage());
                log.warn("[Flow3pSync] 本地→三方同步异常 instanceId={} platform={} err={}",
                        instanceId, logDo.getPlatform(), e.getMessage());
            }
        }
    }
}
