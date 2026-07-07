package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.entity.FlowThirdPartyAccountDO;
import com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO;
import com.njydsz.pmis.workflow.mapper.FlowThirdPartyLogMapper;
import com.njydsz.pmis.workflow.service.FlowThirdPartyAccountService;
import com.njydsz.pmis.workflow.service.FlowThirdPartySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三方审批双向同步服务实现
 *
 * <p>P2-6 (GAP-40): 本地→三方主动同步。
 * 查询该实例关联的三方审批记录，若账号配置了 cancelWebhookUrl 则 POST 调用取消三方审批单；
 * 未配置时标记 NOT_CONFIGURED；调用失败标记 FAIL。所有异常降级记录，不影响本地主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartySyncServiceImpl implements FlowThirdPartySyncService {

    private final FlowThirdPartyLogMapper logMapper;
    private final FlowThirdPartyAccountService accountService;

    /** 轻量 RestTemplate（与 FlowNotificationServiceImpl 一致，直接 new 默认实例） */
    private final RestTemplate restTemplate = new RestTemplate();

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
        List<FlowThirdPartyLogDO> logs;
        try {
            logs = logMapper.selectByBusinessId(instanceId);
        } catch (Exception e) {
            log.warn("[Flow3pSync] 查询三方审批日志失败 instanceId={} err={}", instanceId, e.getMessage());
            return;
        }
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (FlowThirdPartyLogDO logDo : logs) {
            FlowThirdPartyAccountDO account = null;
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
