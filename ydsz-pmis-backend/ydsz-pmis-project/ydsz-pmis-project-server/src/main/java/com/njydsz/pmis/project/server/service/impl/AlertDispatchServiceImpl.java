package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.notify.event.UnifiedAlertEvent;
import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.project.domain.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.domain.entity.AlertDispatchDO;
import com.njydsz.pmis.project.infra.mapper.AlertDispatchMapper;
import com.njydsz.pmis.project.server.engine.AlertCodeGen;
import com.njydsz.pmis.project.server.service.AlertDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /** 预警分发 Mapper */
    private final AlertDispatchMapper mapper;
    /** Spring 事件发布器（P0-2: 统一告警事件总线） */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 自身代理引用，避免内部 this 调用绕过 Spring AOP（@Transactional）。
     * <p>P1-4 修复：retryFailed 通过 this.dispatchNow() 调用时，AOP 注解不生效，导致
     * 事务回滚失效。改为通过 self 代理调用，确保 @Transactional 正常工作。
     * <p>@Lazy 避免循环依赖（self 引用自身 bean）。
     */
    private final AlertDispatchService self;

    public AlertDispatchServiceImpl(AlertDispatchMapper mapper,
                                    ApplicationEventPublisher eventPublisher,
                                    @Lazy AlertDispatchService self) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submit(AlertDispatchDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getAlertType())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_fc360b56");
        }
        String level = StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel().toUpperCase() : "YELLOW";
        if (!isValidLevel(level)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_edec9e26", level);
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_a39a1acf");
        }

        AlertDispatchDO d = new AlertDispatchDO();
        BeanUtils.copyProperties(dto, d);
        d.setAlertLevel(level);
        if (!StringUtils.hasText(d.getAlertCode())) {
            d.setAlertCode(AlertCodeGen.next(dto.getAlertType(), level));
        }
        if (!StringUtils.hasText(d.getPushChannels())) {
            d.setPushChannels(level.equals("RED") ? "INAPP,EMAIL" : "INAPP");
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
    @Transactional(rollbackFor = Exception.class)
    public boolean dispatchNow(String id) {
        if (id == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_411b6827");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_6a72742d");
        }
        if ("SENT".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus())) {
            return true;
        }
        try {
            // P0-2: 通过统一告警事件总线分发
            // 角色解析/通道路由/Feign调用/实时广播全部由 UnifiedAlertDispatcher 统一处理
            UnifiedAlertEvent event = UnifiedAlertEvent.builder()
                    .alertCode(d.getAlertCode())
                    .alertType(d.getAlertType())
                    .alertLevel(d.getAlertLevel())
                    .sourceModule(d.getSourceType())
                    .sourceId(d.getSourceId())
                    .title(d.getTitle())
                    .content(d.getContent())
                    .targetRole(d.getTargetRole())
                    .targetUserIds(d.getTargetUserIds())
                    .pushChannels(d.getPushChannels())
                    .triggeredAt(d.getDispatchedAt())
                    .tenantId(d.getTenantId())
                    .traceId(d.getProviderTraceId())
                    .build();
            eventPublisher.publishEvent(event);

            int n = mapper.markSent(id, LocalDateTime.now());
            log.info("[Alert] 分发成功(统一事件总线): id={} code={} level={} channels={}",
                    id, d.getAlertCode(), d.getAlertLevel(), d.getPushChannels());
            return n > 0;
        } catch (Exception e) {
            mapper.markFailed(id, e.getMessage());
            log.warn("[Alert] 分发失败: id={} err={}", id, e.getMessage());
            return false;
        }
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
                // P1-4: 通过 self 代理调用 dispatchNow，激活 @Transactional 注解
                // （此前 this.dispatchNow() 会绕过 Spring AOP，导致本地事务失效）
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
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_411b6827");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_6a72742d");
        }
        if ("SENT".equals(d.getStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_811c5693");
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
