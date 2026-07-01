package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.engine.AlertCodeGen;
import com.njydsz.pmis.execution.entity.AlertDispatchDO;
import com.njydsz.pmis.execution.feign.MessageServiceClient;
import com.njydsz.pmis.execution.mapper.AlertDispatchMapper;
import com.njydsz.pmis.execution.service.AlertDispatchService;
import com.njydsz.pmis.message.channel.MessageRequest;
import com.njydsz.pmis.message.channel.MessageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
@RequiredArgsConstructor
public class AlertDispatchServiceImpl implements AlertDispatchService {

    private final AlertDispatchMapper mapper;
    private final MessageServiceClient messageClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(AlertDispatchDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(dto.getAlertType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预警类型不能为空");
        }
        String level = StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel().toUpperCase() : "YELLOW";
        if (!isValidLevel(level)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预警等级不合法: " + level);
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预警标题不能为空");
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
        if (d.getTenantId() == null) d.setTenantId(1L);
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
    @Transactional(rollbackFor = Exception.class)
    public boolean dispatchNow(Long id) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "预警不存在");
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
                            firstError = r == null ? "null result" : r.getErrorMessage();
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
            R<MessageResult> r = messageClient.send(req);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                return MessageResult.fail(channel, r == null ? "null response"
                        : (r.getCode() + ":" + r.getMessage()));
            }
            return r.getData();
        } catch (Exception e) {
            log.warn("[Alert] Feign 调用异常: channel={} err={}", channel, e.getMessage());
            return MessageResult.fail(channel, e.getMessage());
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
                if (dispatchNow(d.getId())) {
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
    public List<AlertDispatchDO> listByLevelAndStatus(String level, String status) {
        return mapper.selectByLevelAndStatus(level, status);
    }

    @Override
    public List<Map<String, Object>> aggregateByTypeAndLevel(Long tenantId) {
        return mapper.aggregateByTypeAndLevel(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "预警不存在");
        }
        if ("SENT".equals(d.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已发送的预警不可取消");
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
