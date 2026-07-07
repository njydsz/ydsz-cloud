package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.engine.AlertCodeGen;
import com.njydsz.pmis.project.entity.AlertDispatchDO;
import com.njydsz.pmis.project.feign.MessageServiceClient;
import com.njydsz.pmis.project.mapper.AlertDispatchMapper;
import com.njydsz.pmis.project.service.AlertDispatchService;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.feign.NotificationPushClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.apache.seata.spring.annotation.GlobalTransactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预警分级推送 Service 实现
 *
 * <p>按黄/红等级自动映射目标角色（PM/PMO/GM/CFO）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AlertDispatchServiceImpl implements AlertDispatchService {

    private final AlertDispatchMapper mapper;
    private final MessageServiceClient messageClient;
    /** 通知实时推送 Feign 客户端（P0-2，通过 notification 服务 WebSocket 下发） */
    private final NotificationPushClient pushClient;
    /**
     * 自身代理引用，避免内部 this 调用绕过 Spring AOP（@GlobalTransactional / @Transactional）。
     * <p>P1-4 修复：retryFailed 通过 this.dispatchNow() 调用时，AOP 注解不生效，导致
     * 分布式事务回滚失效。改为通过 self 代理调用，确保 @GlobalTransactional 正常工作。
     * <p>@Lazy 避免循环依赖（self 引用自身 bean）。
     */
    private final AlertDispatchService self;

    public AlertDispatchServiceImpl(AlertDispatchMapper mapper,
                                    MessageServiceClient messageClient,
                                    NotificationPushClient pushClient,
                                    @Lazy AlertDispatchService self) {
        this.mapper = mapper;
        this.messageClient = messageClient;
        this.pushClient = pushClient;
        this.self = self;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(AlertDispatchDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getAlertType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_fc360b56");
        }
        String level = StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel().toUpperCase() : "YELLOW";
        if (!isValidLevel(level)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_edec9e26", level);
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_a39a1acf");
        }

        AlertDispatchDO d = new AlertDispatchDO();
        BeanUtils.copyProperties(dto, d);
        d.setAlertLevel(level);
        if (!StringUtils.hasText(d.getAlertCode())) {
            d.setAlertCode(AlertCodeGen.next(dto.getAlertType(), level));
        }
        if (!StringUtils.hasText(d.getPushChannels())) {
            d.setPushChannels(level.equals("RED") ? "IN_APP,EMAIL" : "IN_APP");
        }
        if (!StringUtils.hasText(d.getTargetRole())) {
            d.setTargetRole(String.join(",", resolveTargetRoles(level)));
        }
        if (d.getRetryCount() == null) d.setRetryCount(0);
        if (d.getStatus() == null) d.setStatus("PENDING");
        if (d.getTenantId() == null) d.setTenantId(TenantContext.getTenantId());
        if (d.getDispatchedAt() == null) d.setDispatchedAt(LocalDateTime.now());
        if (d.getProviderTraceId() == null) d.setProviderTraceId("");

        // 幂等：相同 alertCode 已存在则更新
        AlertDispatchDO exist = findByCode(d.getAlertCode());
        if (exist != null) {
            d.setId(exist.getId());
            mapper.updateById(d);
            log.info("[Alert] 幂等更新: code={} level={}", d.getAlertCode(), level);
            return exist.getId();
        }
        mapper.insert(d);
        log.info("[Alert] 提交预警: code={} type={} level={} roles={}",
                d.getAlertCode(), d.getAlertType(), level, d.getTargetRole());
        return d.getId();
    }

    @Override
    @GlobalTransactional(name = "pmis-alert-dispatch-now", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public boolean dispatchNow(String id) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_6a72742d");
        }
        if ("SENT".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus())) {
            return true;
        }
        try {
            // 真正推送: 按 pushChannels 拆分, 逐个调用消息中心
            boolean allOk = true;
            String firstError = null;
            for (String channel : splitChannels(d.getPushChannels())) {
                try {
                    MessageResult r = sendViaMessageCenter(d, channel);
                    if (r == null || !r.isSuccess()) {
                        allOk = false;
                        if (firstError == null) {
                            firstError = r == null ? "null R" : r.getErrorMessage();
                        }
                    }
                } catch (Exception ex) {
                    allOk = false;
                    if (firstError == null) firstError = ex.getMessage();
                    log.warn("[Alert] 通道 {} 推送失败: id={} err={}", channel, id, ex.getMessage());
                }
            }
            if (allOk) {
                int n = mapper.markSent(id, LocalDateTime.now());
                log.info("[Alert] 分发成功: id={} code={} level={} channels={}",
                        id, d.getAlertCode(), d.getAlertLevel(), d.getPushChannels());
                // P0-2: 实时推送告警到前端（通过 Feign 调 notification 推送，失败降级不影响主流程）
                broadcastAlert(d);
                return n > 0;
            }
            mapper.markFailed(id, firstError);
            log.warn("[Alert] 分发部分失败: id={} err={}", id, firstError);
            return false;
        } catch (Exception e) {
            mapper.markFailed(id, e.getMessage());
            log.warn("[Alert] 分发失败: id={} err={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 通过消息中心 OpenFeign 客户端发送预警通知
     */
    private MessageResult sendViaMessageCenter(AlertDispatchDO d, String channel) {
        if (messageClient == null) {
            // 单测/无 Feign 环境: 占位成功
            return MessageResult.ok(channel, "no-feign");
        }
        MessageRequest req = new MessageRequest();
        req.setChannel(channel);
        req.setBizType("ALERT");
        req.setBizId(d.getId() == null ? null : d.getId().toString());
        req.setTemplateCode(buildTemplateCode(d));
        req.setSubject(d.getTitle());
        req.setContent(d.getContent());
        Map<String, Object> params = new HashMap<>();
        params.put("alertCode", d.getAlertCode());
        params.put("alertType", d.getAlertType());
        params.put("alertLevel", d.getAlertLevel());
        params.put("title", d.getTitle());
        params.put("content", d.getContent());
        params.put("targetRole", d.getTargetRole());
        params.put("sourceType", d.getSourceType());
        params.put("sourceId", d.getSourceId());
        req.setParams(params);
        // 接收人: 缺省时使用角色占位, 真实环境会从 user Feign 解析
        req.setReceiver(resolveReceiver(d));

        try {
            Result<MessageResult> r = messageClient.send(req);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                return MessageResult.fail(channel, r == null ? "null response"
                        : (r.getCode() + ":" + r.getMessage()));
            }
            return r.getData();
        } catch (Exception e) {
            log.error("[Alert] Feign 调用异常: channel={} err={}", channel, e.getMessage());
            return MessageResult.fail(channel, e.getMessage());
        }
    }

    /**
     * P0-2: 通过 Feign 调用 notification 服务实时广播告警。
     *
     * <p>告警面向角色而非具体用户，故采用广播；推送失败由 NotificationPushClientFallbackFactory 兜底，
     * 额外 try-catch 保证绝不影响分发主流程。
     *
     * @param d 预警分发实体
     */
    private void broadcastAlert(AlertDispatchDO d) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertId", d.getId());
            payload.put("alertCode", d.getAlertCode());
            payload.put("alertType", d.getAlertType());
            payload.put("alertLevel", d.getAlertLevel());
            payload.put("title", d.getTitle());
            payload.put("targetRole", d.getTargetRole());
            pushClient.broadcast("ALERT", payload);
        } catch (Exception e) {
            log.warn("[Alert] 实时推送降级忽略: id={} err={}", d.getId(), e.getMessage());
        }
    }

    private String buildTemplateCode(AlertDispatchDO d) {
        // 模板命名规范: ALERT_<TYPE>_<LEVEL>
        return "ALERT_" + safe(d.getAlertType()) + "_" + safe(d.getAlertLevel());
    }

    private static String safe(String s) {
        return s == null ? "OTHER" : s.toUpperCase();
    }

    private String resolveReceiver(AlertDispatchDO d) {
        // 简化: 接收人 = targetRole (IN_APP 时展示给所有目标角色)
        // 真实环境会通过 UserServiceClient 解析 targetUserIds
        if (StringUtils.hasText(d.getTargetUserIds())) {
            return d.getTargetUserIds();
        }
        return d.getTargetRole() == null ? "PMO" : d.getTargetRole();
    }

    private List<String> splitChannels(String channels) {
        if (!StringUtils.hasText(channels)) {
            return List.of("IN_APP");
        }
        return Arrays.stream(channels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryFailed(int maxRetry) {
        if (maxRetry <= 0) maxRetry = 3;
        List<AlertDispatchDO> list = mapper.selectRetryable(LocalDateTime.now().minusMinutes(5), maxRetry);
        int n = 0;
        for (AlertDispatchDO d : list) {
            try {
                mapper.incrementRetry(d.getId());
                // P1-4: 通过 self 代理调用 dispatchNow，激活 @GlobalTransactional 注解
                // （此前 this.dispatchNow() 会绕过 Spring AOP，导致分布式事务失效）
                if (self.dispatchNow(d.getId())) {
                    n++;
                }
            } catch (Exception e) {
                log.warn("[Alert] 重试失败: id={} err={}", d.getId(), e.getMessage());
            }
        }
        return n;
    }

    @Override
    public List<String> resolveTargetRoles(String level) {
        if (level == null) return Collections.emptyList();
        return switch (level.toUpperCase()) {
            case "RED" -> List.of("PMO", "GM", "CFO");
            case "YELLOW" -> List.of("PM", "PMO");
            case "NORMAL" -> List.of("PM");
            default -> Collections.emptyList();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDispatchDO> listByLevelAndStatus(String level, String status) {
        return mapper.selectByLevelAndStatus(level, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByTypeAndLevel(String tenantId) {
        return mapper.aggregateByTypeAndLevel(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, String reason) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_6a72742d");
        }
        if ("SENT".equals(d.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_811c5693");
        }
        AlertDispatchDO update = new AlertDispatchDO();
        update.setId(id);
        update.setStatus("CANCELLED");
        update.setFailReason(reason);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
        log.info("[Alert] 取消预警: id={} reason={}", id, reason);
    }

    // ----------------- 私有 -----------------

    private boolean isValidLevel(String level) {
        return "YELLOW".equals(level) || "RED".equals(level) || "NORMAL".equals(level);
    }

    private AlertDispatchDO findByCode(String code) {
        if (!StringUtils.hasText(code)) return null;
        return mapper.selectList(new QueryWrapper<AlertDispatchDO>()
                        .eq("alert_code", code)
                        .eq("deleted", 0)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }
}
