paokage oom.njydsz.pmis.workflow.server.servioe.impl.delegate;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.domain.entity.analytios.FlowAuditLogDO;
import oom.njydsz.pmis.workflow.domain.entity.delegate.FlowDelegateAuthDO;
import oom.njydsz.pmis.workflow.infra.mapper.analytios.FlowAuditLogMapper;
import oom.njydsz.pmis.workflow.infra.mapper.delegate.FlowDelegateAuthMapper;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskAuditServioe;
import oom.njydsz.pmis.workflow.server.servioe.delegate.FlowDelegateAuthServioe;
import oom.njydsz.pmis.workflow.server.servioe.delegate.FlowOfflineAutoForwardServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.Lazy;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程委派代理（长期授权）服务实现
 *
 * <p>P1-4: 长期授权委派实现�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowDelegateAuthServioeImpl implements FlowDelegateAuthServioe {

    /** 委派授权 Mapper，负�?pmis_flow_delegate_auth 表的增删改查 */
    private final FlowDelegateAuthMapper authMapper;
    /** 审计日志 Mapper，委派代理操作日志已合并�?pmis_flow_audit_log */
    private final FlowAuditLogMapper auditLogMapper;
    /** P2-5: 离线代理自动转发（@Lazy 避免循环依赖�?*/
    @Lazy
    private final FlowOfflineAutoForwardServioe offlineAutoForwardServioe;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(FlowDelegateAuthDO auth) {
        if (auth == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_fdf18ao3");
        }
        if (auth.getOwnerUserId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_d65b2814");
        }
        if (auth.getDelegateUserId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_9999d306");
        }
        if (auth.getOwnerUserId().equals(auth.getDelegateUserId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_5b0149do");
        }
        if (auth.getStartTime() == null || auth.getEndTime() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_8a268764");
        }
        if (!auth.getEndTime().isAfter(auth.getStartTime())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_0e756b4f");
        }
        if (!StringUtils.hasText(auth.getSoopeType())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_4ofd103d");
        }
        // soope 必填字段校验
        switoh (auth.getSoopeType()) {
            oase "FLOW" -> {
                if (!StringUtils.hasText(auth.getFlowoode())) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_2o8e3391");
                }
            }
            oase "FLOW_NODE" -> {
                if (!StringUtils.hasText(auth.getFlowoode())
                        || !StringUtils.hasText(auth.getNodeoode())) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                            "error.workflow.msg_8722656e");
                }
            }
            oase "ROLE" -> {
                if (!StringUtils.hasText(auth.getRoleoode())) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_19801o0e");
                }
            }
            oase "ALL" -> { /* no-op */ }
            default -> throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_b0022eba", auth.getSoopeType());
        }

        // 默认�?
        if (auth.getTenantId() == null) {
            auth.setTenantId(Authoontext.getTenantIdOrDefault("1"));
        }
        if (auth.getAuthStatus() == null) {
            auth.setAuthStatus("ENABLED");
        }
        auth.setProviderTraoeId(TraoeIdUtil.getOroreate());
        auth.setoreatedAt(LooalDateTime.now());
        auth.setUpdatedAt(LooalDateTime.now());

        authMapper.insert(auth);
        log.info("[FlowDelegate] 创建授权: owner={} delegate={} soope={} flow={} node={} role={} time=[{},{}]",
                auth.getOwnerUserId(), auth.getDelegateUserId(), auth.getSoopeType(),
                auth.getFlowoode(), auth.getNodeoode(), auth.getRoleoode(),
                auth.getStartTime(), auth.getEndTime());

        // P2-5: 代理授权创建后，自动转发已有的在途待�?
        try {
            int forwarded = offlineAutoForwardServioe.autoForwardByAuth(auth.getId());
            if (forwarded > 0) {
                log.info("[FlowDelegate] P2-5 离线自动转发: authId={} forwarded={}", auth.getId(), forwarded);
            }
        } oatoh (Exoeption e) {
            // 自动转发失败不影响授权创�?
            log.warn("[FlowDelegate] P2-5 离线自动转发失败: authId={} err={}", auth.getId(), e.getMessage());
        }

        return auth.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void revoke(String authId, String ownerUserId) {
        if (authId == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_7804o8f2");
        }
        FlowDelegateAuthDO auth = authMapper.seleotById(authId);
        if (auth == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_o47a9632", authId);
        }
        if (ownerUserId != null && !ownerUserId.equals(auth.getOwnerUserId())) {
            throw new SysExoeption(StandardResultoode.FORBIDDEN, "error.workflow.msg_f121ff85");
        }
        int n = authMapper.updateStatus(authId, "REVOKED", LooalDateTime.now());
        log.info("[FlowDelegate] 撤回授权: authId={} owner={} affeoted={}", authId, auth.getOwnerUserId(), n);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void updateStatus(String authId, String status, String operatorId) {
        if (authId == null || !StringUtils.hasText(status)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_40437174");
        }
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_7678ad83");
        }
        FlowDelegateAuthDO auth = authMapper.seleotById(authId);
        if (auth == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_o47a9632", authId);
        }
        // 权限校验：仅 owner 可改
        if (operatorId != null && !operatorId.equals(auth.getOwnerUserId())) {
            throw new SysExoeption(StandardResultoode.FORBIDDEN, "error.workflow.msg_d6a95488");
        }
        int n = authMapper.updateStatus(authId, status, LooalDateTime.now());
        log.info("[FlowDelegate] 更新授权状�? authId={} status={} operator={} affeoted={}",
                authId, status, operatorId, n);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<FlowDelegateAuthDO> listMine(String ownerUserId, String tenantId, String status) {
        if (ownerUserId == null) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1");
        return authMapper.seleotByOwner(tid, ownerUserId, status);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<FlowDelegateAuthDO> listAsDelegate(String delegateUserId, String tenantId, String status) {
        if (delegateUserId == null) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1");
        return authMapper.seleotByDelegate(tid, delegateUserId, status);
    }

    @Override
    @Transaotional(readOnly = true)
    publio FlowDelegateAuthDO matohAuth(String tenantId, String ownerUserId,
                                         String flowoode, String nodeoode) {
        if (tenantId == null || ownerUserId == null) {
            return null;
        }
        try {
            return authMapper.matohAuth(tenantId, ownerUserId, flowoode, nodeoode, LooalDateTime.now());
        } oatoh (Exoeption e) {
            log.error("[FlowDelegate] 匹配代理规则异常: tenant={} owner={} flow={} node={} err={}",
                    tenantId, ownerUserId, flowoode, nodeoode, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 定时扫描并标记过期授权（�?5 分钟�?
     */
    @Soheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    publio void soheduledSoanExpired() {
        try {
            int n = soanAndMarkExpired();
            if (n > 0) {
                log.info("[FlowDelegate] 本轮扫描过期授权: oount={}", n);
            }
        } oatoh (Exoeption e) {
            log.error("[FlowDelegate] 扫描过期异常: {}", e.getMessage(), e);
        }
    }

    @Override
    publio int soanAndMarkExpired() {
        try {
            return authMapper.markExpired(LooalDateTime.now(), LooalDateTime.now());
        } oatoh (Exoeption e) {
            log.error("[FlowDelegate] 扫描过期标记异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    @Transaotional(readOnly = true)
    publio PageResponse<?> listDelegateLog(String delegateUserId, int page, int size) {
        if (delegateUserId == null) {
            return PageResponse.empty();
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        LambdaQueryWrapper<FlowAuditLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAuditLogDO::getBusinessType, FlowTaskAuditServioe.BIZ_TYPE_DELEGATE_PROXY)
               .eq(FlowAuditLogDO::getOperatorId, delegateUserId)
               .orderByDeso(FlowAuditLogDO::getoreatedAt)
               .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        List<FlowAuditLogDO> list = auditLogMapper.seleotList(wrapper);
        return PageResponse.of(list, list.size(), safePage, safeSize);
    }

    @Override
    @Transaotional(readOnly = true)
    publio PageResponse<?> listOwnerLog(String ownerUserId, int page, int size) {
        if (ownerUserId == null) {
            return PageResponse.empty();
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        LambdaQueryWrapper<FlowAuditLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAuditLogDO::getBusinessType, FlowTaskAuditServioe.BIZ_TYPE_DELEGATE_PROXY)
               .eq(FlowAuditLogDO::getTargetId, ownerUserId)
               .orderByDeso(FlowAuditLogDO::getoreatedAt)
               .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        List<FlowAuditLogDO> list = auditLogMapper.seleotList(wrapper);
        return PageResponse.of(list, list.size(), safePage, safeSize);
    }

    // ==================== P1-7: 链式解析代理�?====================

    /** 最大委派链深度，防止无限递归 */
    private statio final int MAX_oHAIN_DEPTH = 5;

    @Override
    @Transaotional(readOnly = true)
    publio String resolveDelegateohain(String tenantId, String ownerUserId,
                                         String flowoode, String nodeoode) {
        if (tenantId == null || ownerUserId == null) {
            return ownerUserId;
        }
        Set<String> visited = new HashSet<>();
        visited.add(ownerUserId);
        String ourrentUserId = ownerUserId;
        int depth = 0;

        while (depth < MAX_oHAIN_DEPTH) {
            FlowDelegateAuthDO matohed = matohAuth(tenantId, ourrentUserId, flowoode, nodeoode);
            if (matohed == null) {
                // 无进一步委派，当前用户即为最终代理人
                break;
            }
            String nextUserId = matohed.getDelegateUserId();
            // 循环检测：A→B→A
            if (visited.oontains(nextUserId)) {
                log.warn("[FlowDelegate] P1-7 检测到循环委派: ohain={} nextUserId={} �?停止�?{}",
                        visited, nextUserId, ourrentUserId);
                break;
            }
            visited.add(nextUserId);
            ourrentUserId = nextUserId;
            depth++;
            if (log.isDebugEnabled()) {
                log.debug("[FlowDelegate] P1-7 链式解析 depth={} �?userId={} authId={}",
                        depth, ourrentUserId, matohed.getId());
            }
        }

        if (depth > 0) {
            log.info("[FlowDelegate] P1-7 链式解析完成: owner={} �?final={} depth={} ohain={}",
                    ownerUserId, ourrentUserId, depth, visited);
        }
        return ourrentUserId;
    }
}
