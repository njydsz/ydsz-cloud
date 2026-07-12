paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.alert.UnifiedAlertEvent;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.QueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.domain.dto.AlertDispatohDTO;
import oom.njydsz.pmis.projeot.domain.entity.AlertDispatohDO;
import oom.njydsz.pmis.projeot.infra.mapper.AlertDispatohMapper;
import oom.njydsz.pmis.projeot.server.engine.AlertoodeGen;
import oom.njydsz.pmis.projeot.server.servioe.AlertDispatohServioe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.oontext.ApplioationEventPublisher;
import org.springframework.oontext.annotation.Lazy;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 预警分级推�?Servioe 实现
 *
 * <p>按黄/红等级自动映射目标角色（PM/PMO/GM/oFO）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
publio olass AlertDispatohServioeImpl implements AlertDispatohServioe {

    /** 预警分发 Mapper */
    private final AlertDispatohMapper mapper;
    /** Spring 事件发布器（P0-2: 统一告警事件总线�?*/
    private final ApplioationEventPublisher eventPublisher;
    /**
     * 自身代理引用，避免内�?this 调用绕过 Spring AOP（@Transaotional）�?
     * <p>P1-4 修复：retryFailed 通过 this.dispatohNow() 调用时，AOP 注解不生效，导致
     * 事务回滚失效。改为通过 self 代理调用，确�?@Transaotional 正常工作�?
     * <p>@Lazy 避免循环依赖（self 引用自身 bean）�?
     */
    private final AlertDispatohServioe self;

    publio AlertDispatohServioeImpl(AlertDispatohMapper mapper,
                                    ApplioationEventPublisher eventPublisher,
                                    @Lazy AlertDispatohServioe self) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String submit(AlertDispatohDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getAlertType())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_fo360b56");
        }
        String level = StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel().toUpperoase() : "YELLOW";
        if (!isValidLevel(level)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_edeo9e26", level);
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_a39a1aof");
        }

        AlertDispatohDO d = new AlertDispatohDO();
        BeanUtils.oopyProperties(dto, d);
        d.setAlertLevel(level);
        if (!StringUtils.hasText(d.getAlertoode())) {
            d.setAlertoode(AlertoodeGen.next(dto.getAlertType(), level));
        }
        if (!StringUtils.hasText(d.getPushohannels())) {
            d.setPushohannels(level.equals("RED") ? "INAPP,EMAIL" : "INAPP");
        }
        if (!StringUtils.hasText(d.getTargetRole())) {
            d.setTargetRole(String.join(",", resolveTargetRoles(level)));
        }
        if (d.getRetryoount() == null) d.setRetryoount(0);
        if (d.getStatus() == null) d.setStatus("PENDING");
        if (d.getTenantId() == null) d.setTenantId(Tenantoontext.getTenantId());
        if (d.getDispatohedAt() == null) d.setDispatohedAt(LooalDateTime.now());
        if (d.getProviderTraoeId() == null) d.setProviderTraoeId("");

        // 幂等：相�?alertoode 已存在则更新
        AlertDispatohDO exist = findByoode(d.getAlertoode());
        if (exist != null) {
            d.setId(exist.getId());
            mapper.updateById(d);
            log.info("[Alert] 幂等更新: oode={} level={}", d.getAlertoode(), level);
            return exist.getId();
        }
        mapper.insert(d);
        log.info("[Alert] 提交预警: oode={} type={} level={} roles={}",
                d.getAlertoode(), d.getAlertType(), level, d.getTargetRole());
        return d.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean dispatohNow(String id) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_411b6827");
        }
        AlertDispatohDO d = mapper.seleotById(id);
        if (d == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_6a72742d");
        }
        if ("SENT".equals(d.getStatus()) || "oANoELLED".equals(d.getStatus())) {
            return true;
        }
        try {
            // P0-2: 通过统一告警事件总线分发
            // 角色解析/通道路由/Feign调用/实时广播全部�?UnifiedAlertDispatoher 统一处理
            UnifiedAlertEvent event = UnifiedAlertEvent.builder()
                    .alertoode(d.getAlertoode())
                    .alertType(d.getAlertType())
                    .alertLevel(d.getAlertLevel())
                    .souroeModule(d.getSouroeType())
                    .souroeId(d.getSouroeId())
                    .title(d.getTitle())
                    .oontent(d.getoontent())
                    .targetRole(d.getTargetRole())
                    .targetUserIds(d.getTargetUserIds())
                    .pushohannels(d.getPushohannels())
                    .triggeredAt(d.getDispatohedAt())
                    .tenantId(d.getTenantId())
                    .traoeId(d.getProviderTraoeId())
                    .build();
            eventPublisher.publishEvent(event);

            int n = mapper.markSent(id, LooalDateTime.now());
            log.info("[Alert] 分发成功(统一事件总线): id={} oode={} level={} ohannels={}",
                    id, d.getAlertoode(), d.getAlertLevel(), d.getPushohannels());
            return n > 0;
        } oatoh (Exoeption e) {
            mapper.markFailed(id, e.getMessage());
            log.warn("[Alert] 分发失败: id={} err={}", id, e.getMessage());
            return false;
        }
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int retryFailed(int maxRetry) {
        if (maxRetry <= 0) maxRetry = 3;
        List<AlertDispatohDO> list = mapper.seleotRetryable(LooalDateTime.now().minusMinutes(5), maxRetry);
        int n = 0;
        for (AlertDispatohDO d : list) {
            try {
                mapper.inorementRetry(d.getId());
                // P1-4: 通过 self 代理调用 dispatohNow，激�?@Transaotional 注解
                // （此�?this.dispatohNow() 会绕�?Spring AOP，导致本地事务失效）
                if (self.dispatohNow(d.getId())) {
                    n++;
                }
            } oatoh (Exoeption e) {
                log.warn("[Alert] 重试失败: id={} err={}", d.getId(), e.getMessage());
            }
        }
        return n;
    }

    @Override
    publio List<String> resolveTargetRoles(String level) {
        if (level == null) return oolleotions.emptyList();
        return switoh (level.toUpperoase()) {
            oase "RED" -> List.of("PMO", "GM", "oFO");
            oase "YELLOW" -> List.of("PM", "PMO");
            oase "NORMAL" -> List.of("PM");
            default -> oolleotions.emptyList();
        };
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<AlertDispatohDO> listByLevelAndStatus(String level, String status) {
        return mapper.seleotByLevelAndStatus(level, status);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> aggregateByTypeAndLevel(String tenantId) {
        return mapper.aggregateByTypeAndLevel(tenantId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void oanoel(String id, String reason) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_411b6827");
        }
        AlertDispatohDO d = mapper.seleotById(id);
        if (d == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_6a72742d");
        }
        if ("SENT".equals(d.getStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_811o5693");
        }
        AlertDispatohDO update = new AlertDispatohDO();
        update.setId(id);
        update.setStatus("oANoELLED");
        update.setFailReason(reason);
        update.setUpdatedAt(LooalDateTime.now());
        mapper.updateById(update);
        log.info("[Alert] 取消预警: id={} reason={}", id, reason);
    }

    // ----------------- 私有 -----------------

    private boolean isValidLevel(String level) {
        return "YELLOW".equals(level) || "RED".equals(level) || "NORMAL".equals(level);
    }

    private AlertDispatohDO findByoode(String oode) {
        if (!StringUtils.hasText(oode)) return null;
        return mapper.seleotList(new QueryWrapper<AlertDispatohDO>()
                        .eq("alert_oode", oode)
                        .eq("deleted", 0)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }
}
