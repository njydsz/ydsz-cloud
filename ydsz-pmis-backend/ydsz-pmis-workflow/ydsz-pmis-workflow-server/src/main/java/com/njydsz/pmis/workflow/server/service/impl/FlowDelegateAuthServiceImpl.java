package com.njydsz.pmis.workflow.server.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.util.id.TracerUtils;
import com.njydsz.pmis.workflow.domain.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.domain.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowDelegateAuthMapper;
import com.njydsz.pmis.workflow.server.service.FlowDelegateAuthService;
import com.njydsz.pmis.workflow.server.service.FlowOfflineAutoForwardService;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowTaskAuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程委派代理（长期授权）服务实现
 *
 * <p>P1-4: 长期授权委派实现。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDelegateAuthServiceImpl implements FlowDelegateAuthService {

    /** 委派授权 Mapper，负责 pmis_flow_delegate_auth 表的增删改查 */
    private final FlowDelegateAuthMapper authMapper;
    /** 审计日志 Mapper，委派代理操作日志已合并到 pmis_flow_audit_log */
    private final FlowAuditLogMapper auditLogMapper;
    /** P2-5: 离线代理自动转发（@Lazy 避免循环依赖） */
    @Lazy
    private final FlowOfflineAutoForwardService offlineAutoForwardService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowDelegateAuthDO auth) {
        if (auth == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_fdf18ac3");
        }
        if (auth.getOwnerUserId() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d65b2814");
        }
        if (auth.getDelegateUserId() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_9999d306");
        }
        if (auth.getOwnerUserId().equals(auth.getDelegateUserId())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_5b0149dc");
        }
        if (auth.getStartTime() == null || auth.getEndTime() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_8a268764");
        }
        if (!auth.getEndTime().isAfter(auth.getStartTime())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_0e756b4f");
        }
        if (!StringUtils.hasText(auth.getScopeType())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_4cfd103d");
        }
        // scope 必填字段校验
        switch (auth.getScopeType()) {
            case "FLOW" -> {
                if (!StringUtils.hasText(auth.getFlowCode())) {
                    throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_2c8e3391");
                }
            }
            case "FLOW_NODE" -> {
                if (!StringUtils.hasText(auth.getFlowCode())
                        || !StringUtils.hasText(auth.getNodeCode())) {
                    throw new SysException(BaseResultCode.BAD_REQUEST,
                            "error.workflow.msg_8722656e");
                }
            }
            case "ROLE" -> {
                if (!StringUtils.hasText(auth.getRoleCode())) {
                    throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_19801c0e");
                }
            }
            case "ALL" -> { /* no-op */ }
            default -> throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_b0022eba", auth.getScopeType());
        }

        // 默认值
        if (auth.getTenantId() == null) {
            auth.setTenantId(AuthContext.getTenantIdOrDefault("1"));
        }
        if (auth.getAuthStatus() == null) {
            auth.setAuthStatus("ENABLED");
        }
        auth.setProviderTraceId(TracerUtils.getOrCreateTraceId());
        auth.setCreatedAt(LocalDateTime.now());
        auth.setUpdatedAt(LocalDateTime.now());

        authMapper.insert(auth);
        log.info("[FlowDelegate] 创建授权: owner={} delegate={} scope={} flow={} node={} role={} time=[{},{}]",
                auth.getOwnerUserId(), auth.getDelegateUserId(), auth.getScopeType(),
                auth.getFlowCode(), auth.getNodeCode(), auth.getRoleCode(),
                auth.getStartTime(), auth.getEndTime());

        // P2-5: 代理授权创建后，自动转发已有的在途待办
        try {
            int forwarded = offlineAutoForwardService.autoForwardByAuth(auth.getId());
            if (forwarded > 0) {
                log.info("[FlowDelegate] P2-5 离线自动转发: authId={} forwarded={}", auth.getId(), forwarded);
            }
        } catch (Exception e) {
            // 自动转发失败不影响授权创建
            log.warn("[FlowDelegate] P2-5 离线自动转发失败: authId={} err={}", auth.getId(), e.getMessage());
        }

        return auth.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String authId, String ownerUserId) {
        if (authId == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_7804c8f2");
        }
        FlowDelegateAuthDO auth = authMapper.selectById(authId);
        if (auth == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_c47a9632", authId);
        }
        if (ownerUserId != null && !ownerUserId.equals(auth.getOwnerUserId())) {
            throw new SysException(BaseResultCode.FORBIDDEN, "error.workflow.msg_f121ff85");
        }
        int n = authMapper.updateStatus(authId, "REVOKED", LocalDateTime.now());
        log.info("[FlowDelegate] 撤回授权: authId={} owner={} affected={}", authId, auth.getOwnerUserId(), n);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String authId, String status, String operatorId) {
        if (authId == null || !StringUtils.hasText(status)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_40437174");
        }
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_7678ad83");
        }
        FlowDelegateAuthDO auth = authMapper.selectById(authId);
        if (auth == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_c47a9632", authId);
        }
        // 权限校验：仅 owner 可改
        if (operatorId != null && !operatorId.equals(auth.getOwnerUserId())) {
            throw new SysException(BaseResultCode.FORBIDDEN, "error.workflow.msg_d6a95488");
        }
        int n = authMapper.updateStatus(authId, status, LocalDateTime.now());
        log.info("[FlowDelegate] 更新授权状态: authId={} status={} operator={} affected={}",
                authId, status, operatorId, n);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowDelegateAuthDO> listMine(String ownerUserId, String tenantId, String status) {
        if (ownerUserId == null) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return authMapper.selectByOwner(tid, ownerUserId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowDelegateAuthDO> listAsDelegate(String delegateUserId, String tenantId, String status) {
        if (delegateUserId == null) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return authMapper.selectByDelegate(tid, delegateUserId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public FlowDelegateAuthDO matchAuth(String tenantId, String ownerUserId,
                                        String flowCode, String nodeCode) {
        if (tenantId == null || ownerUserId == null) {
            return null;
        }
        try {
            return authMapper.matchAuth(tenantId, ownerUserId, flowCode, nodeCode, LocalDateTime.now());
        } catch (Exception e) {
            log.error("[FlowDelegate] 匹配代理规则异常: tenant={} owner={} flow={} node={} err={}",
                    tenantId, ownerUserId, flowCode, nodeCode, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 定时扫描并标记过期授权（每 5 分钟）
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void scheduledScanExpired() {
        try {
            int n = scanAndMarkExpired();
            if (n > 0) {
                log.info("[FlowDelegate] 本轮扫描过期授权: count={}", n);
            }
        } catch (Exception e) {
            log.error("[FlowDelegate] 扫描过期异常: {}", e.getMessage(), e);
        }
    }

    @Override
    public int scanAndMarkExpired() {
        try {
            return authMapper.markExpired(LocalDateTime.now(), LocalDateTime.now());
        } catch (Exception e) {
            log.error("[FlowDelegate] 扫描过期标记异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<?> listDelegateLog(String delegateUserId, int page, int size) {
        if (delegateUserId == null) {
            return (PageResponse) PageResponse.success(null);
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        LambdaQueryWrapper<FlowAuditLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAuditLogDO::getBusinessType, FlowTaskAuditService.BIZ_TYPE_DELEGATE_PROXY)
                .eq(FlowAuditLogDO::getOperatorId, delegateUserId)
                .orderByDesc(FlowAuditLogDO::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        List<FlowAuditLogDO> list = auditLogMapper.selectList(wrapper);
        return (PageResponse) PageResponse.success((long) list.size(), (long) safePage, (long) safeSize, list);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<?> listOwnerLog(String ownerUserId, int page, int size) {
        if (ownerUserId == null) {
            return (PageResponse) PageResponse.success(null);
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        LambdaQueryWrapper<FlowAuditLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAuditLogDO::getBusinessType, FlowTaskAuditService.BIZ_TYPE_DELEGATE_PROXY)
                .eq(FlowAuditLogDO::getTargetId, ownerUserId)
                .orderByDesc(FlowAuditLogDO::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        List<FlowAuditLogDO> list = auditLogMapper.selectList(wrapper);
        return (PageResponse) PageResponse.success((long) list.size(), (long) safePage, (long) safeSize, list);
    }

    // ==================== P1-7: 链式解析代理人 ====================

    /** 最大委派链深度，防止无限递归 */
    private static final int MAX_CHAIN_DEPTH = 5;

    @Override
    @Transactional(readOnly = true)
    public String resolveDelegateChain(String tenantId, String ownerUserId,
                                       String flowCode, String nodeCode) {
        if (tenantId == null || ownerUserId == null) {
            return ownerUserId;
        }
        Set<String> visited = new HashSet<>();
        visited.add(ownerUserId);
        String currentUserId = ownerUserId;
        int depth = 0;

        while (depth < MAX_CHAIN_DEPTH) {
            FlowDelegateAuthDO matched = matchAuth(tenantId, currentUserId, flowCode, nodeCode);
            if (matched == null) {
                // 无进一步委派，当前用户即为最终代理人
                break;
            }
            String nextUserId = matched.getDelegateUserId();
            // 循环检测：A→B→A
            if (visited.contains(nextUserId)) {
                log.warn("[FlowDelegate] P1-7 检测到循环委派: chain={} nextUserId={} → 停止于 {}",
                        visited, nextUserId, currentUserId);
                break;
            }
            visited.add(nextUserId);
            currentUserId = nextUserId;
            depth++;
            if (log.isDebugEnabled()) {
                log.debug("[FlowDelegate] P1-7 链式解析 depth={} → userId={} authId={}",
                        depth, currentUserId, matched.getId());
            }
        }

        if (depth > 0) {
            log.info("[FlowDelegate] P1-7 链式解析完成: owner={} → final={} depth={} chain={}",
                    ownerUserId, currentUserId, depth, visited);
        }
        return currentUserId;
    }
}
