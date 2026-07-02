package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.entity.FlowDelegateLogDO;
import com.njydsz.pmis.workflow.mapper.FlowDelegateAuthMapper;
import com.njydsz.pmis.workflow.mapper.FlowDelegateLogMapper;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

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

    private final FlowDelegateAuthMapper authMapper;
    private final FlowDelegateLogMapper logMapper;

    /** 扫描批大小 */
    private static final int SCAN_BATCH_SIZE = 200;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FlowDelegateAuthDO auth) {
        if (auth == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "授权信息不能为空");
        }
        if (auth.getOwnerUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "授权人不能为空");
        }
        if (auth.getDelegateUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "被授权人不能为空");
        }
        if (auth.getOwnerUserId().equals(auth.getDelegateUserId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "被授权人不能是自己");
        }
        if (auth.getStartTime() == null || auth.getEndTime() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "生效时间不能为空");
        }
        if (!auth.getEndTime().isAfter(auth.getStartTime())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        if (!StringUtils.hasText(auth.getScopeType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "scopeType 不能为空");
        }
        // scope 必填字段校验
        switch (auth.getScopeType()) {
            case "FLOW" -> {
                if (!StringUtils.hasText(auth.getFlowCode())) {
                    throw new BizException(BizErrorCode.BAD_REQUEST, "FLOW 模式需指定 flowCode");
                }
            }
            case "FLOW_NODE" -> {
                if (!StringUtils.hasText(auth.getFlowCode())
                        || !StringUtils.hasText(auth.getNodeCode())) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "FLOW_NODE 模式需指定 flowCode 和 nodeCode");
                }
            }
            case "ROLE" -> {
                if (!StringUtils.hasText(auth.getRoleCode())) {
                    throw new BizException(BizErrorCode.BAD_REQUEST, "ROLE 模式需指定 roleCode");
                }
            }
            case "ALL" -> { /* no-op */ }
            default -> throw new BizException(BizErrorCode.BAD_REQUEST,
                    "不支持的 scopeType: " + auth.getScopeType());
        }

        // 默认值
        if (auth.getTenantId() == null) {
            auth.setTenantId(SecurityContext.getTenantIdOrDefault(1L));
        }
        if (auth.getAuthStatus() == null) {
            auth.setAuthStatus("ENABLED");
        }
        auth.setProviderTraceId(TraceIdUtil.getOrCreate());
        auth.setCreatedAt(LocalDateTime.now());
        auth.setUpdatedAt(LocalDateTime.now());

        authMapper.insert(auth);
        log.info("[FlowDelegate] 创建授权: owner={} delegate={} scope={} flow={} node={} role={} time=[{},{}]",
                auth.getOwnerUserId(), auth.getDelegateUserId(), auth.getScopeType(),
                auth.getFlowCode(), auth.getNodeCode(), auth.getRoleCode(),
                auth.getStartTime(), auth.getEndTime());
        return auth.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long authId, Long ownerUserId) {
        if (authId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "authId 不能为空");
        }
        FlowDelegateAuthDO auth = authMapper.selectById(authId);
        if (auth == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "授权记录不存在: " + authId);
        }
        if (ownerUserId != null && !ownerUserId.equals(auth.getOwnerUserId())) {
            throw new BizException(BizErrorCode.FORBIDDEN, "仅授权人本人可撤回");
        }
        int n = authMapper.updateStatus(authId, "REVOKED", LocalDateTime.now());
        log.info("[FlowDelegate] 撤回授权: authId={} owner={} affected={}", authId, auth.getOwnerUserId(), n);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long authId, String status, Long operatorId) {
        if (authId == null || !StringUtils.hasText(status)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "参数不能为空");
        }
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅支持 ENABLED / DISABLED");
        }
        FlowDelegateAuthDO auth = authMapper.selectById(authId);
        if (auth == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "授权记录不存在: " + authId);
        }
        // 权限校验：仅 owner 可改
        if (operatorId != null && !operatorId.equals(auth.getOwnerUserId())) {
            throw new BizException(BizErrorCode.FORBIDDEN, "仅授权人本人可改状态");
        }
        int n = authMapper.updateStatus(authId, status, LocalDateTime.now());
        log.info("[FlowDelegate] 更新授权状态: authId={} status={} operator={} affected={}",
                authId, status, operatorId, n);
    }

    @Override
    public List<FlowDelegateAuthDO> listMine(Long ownerUserId, Long tenantId, String status) {
        if (ownerUserId == null) {
            return List.of();
        }
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return authMapper.selectByOwner(tid, ownerUserId, status);
    }

    @Override
    public List<FlowDelegateAuthDO> listAsDelegate(Long delegateUserId, Long tenantId, String status) {
        if (delegateUserId == null) {
            return List.of();
        }
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return authMapper.selectByDelegate(tid, delegateUserId, status);
    }

    @Override
    public FlowDelegateAuthDO matchAuth(Long tenantId, Long ownerUserId,
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
    public PageResult<FlowDelegateLogDO> listDelegateLog(Long delegateUserId, int page, int size) {
        if (delegateUserId == null) {
            return PageResult.empty();
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowDelegateLogDO> list = logMapper.selectByDelegateUser(delegateUserId, offset, safeSize);
        // 简化：直接用 list.size() 作为 total，更精确可以加 count
        return PageResult.of(list, list.size(), safePage, safeSize);
    }

    @Override
    public PageResult<FlowDelegateLogDO> listOwnerLog(Long ownerUserId, int page, int size) {
        if (ownerUserId == null) {
            return PageResult.empty();
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowDelegateLogDO> list = logMapper.selectByOwnerUser(ownerUserId, offset, safeSize);
        return PageResult.of(list, list.size(), safePage, safeSize);
    }
}
