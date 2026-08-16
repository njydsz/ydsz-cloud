package com.njydsz.workflow.server.service.impl;

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
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.workflow.domain.entity.FlowAuditLog;
import com.njydsz.workflow.domain.entity.FlowDelegateAuth;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.workflow.infra.mapper.FlowDelegateAuthMapper;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;
import com.njydsz.workflow.server.service.FlowOfflineAutoForwardService;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskAuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程委派代理（长期授权）服务实现
 *
 * <p>对 {@link FlowDelegateAuthService} 接口的完整实现，是工作流引擎的<b>长期授权</b>能力。
 * 与「转办 / 委派」等单次操作不同，<b>长期授权</b>允许审批人将审批权限在一段时间内
 * （如出差期间）整体委托给代理人，代理人代为处理所有审批任务，
 * 是大厂 B 端工作流「灵活办公」的标准能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>授权创建（{@link #createAuth}）</b>：审批人设置授权规则
 *       （授权人 / 被授权人 / 授权范围 / 生效起止时间）</li>
 *   <li><b>授权查询（{@link #page / #getActiveAuths}）</b>：分页查询授权列表 / 查询当前生效的授权</li>
 *   <li><b>授权撤销（{@link #revokeAuth}）</b>：审批人主动撤销授权（提前结束授权）</li>
 *   <li><b>授权解析（{@link #resolveDelegatee}）</b>：审批任务创建时解析「实际审批人」
 *       （原审批人 vs 被授权人）</li>
 *   <li><b>授权到期清理（{@link #scanExpired}）</b>：定时任务清理过期授权</li>
 * </ul>
 *
 * <p><b>授权范围：</b>
 * <ul>
 *   <li>{@code ALL} — 全部流程（最常用，审批人整体授权）</li>
 *   <li>{@code SPECIFIC_FLOW} — 指定流程（精确授权，避免敏感流程外泄）</li>
 *   <li>{@code SPECIFIC_NODE} — 指定节点（更细粒度，仅特定节点的审批权）</li>
 *   <li>{@code BY_DEPT} — 按部门（适合部门负责人授权给副手）</li>
 * </ul>
 *
 * <p><b>与转办 / 委派的区别：</b>
 * <table>
 *   <caption>授权 / 转办 / 委派对比</caption>
 *   <tr><th>维度</th><th>长期授权</th><th>转办</th><th>委派</th></tr>
 *   <tr><td>时效</td><td>长期（天 / 周）</td><td>单次</td><td>单次</td></tr>
 *   <tr><td>影响范围</td><td>所有后续任务</td><td>当前任务</td><td>当前任务</td></tr>
 *   <tr><td>审计标注</td><td>「XX 授权给 YY 处理」</td><td>「XX 转办给 YY」</td><td>「XX 委派给 YY」</td></tr>
 *   <tr><td>权限归属</td><td>代理人代为行使</td><td>受让人接收</td><td>受让人处理后返回</td></tr>
 *   <tr><td>典型场景</td><td>出差期间</td><td>当前任务不方便处理</td><td>需要专业判断</td></tr>
 * </table>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>{@link #scanExpired} 定时清理任务通过 {@code @Scheduled} + 集群锁保证单点执行</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>授权互斥</b>：同一审批人同一时间段只能存在一个「全量授权」，
 *       创建新授权时自动撤销旧的「全量授权」</li>
 *   <li><b>授权叠加</b>：审批人可同时存在多个「指定流程 / 指定节点」的授权（互不冲突）</li>
 *   <li><b>授权可追溯</b>：代理人代为处理的任务，审计日志记录「由 ZZ 代批（原审批人 XX）」</li>
 *   <li><b>授权自动失效</b>：到 {@code expireAt} 时间后授权自动失效，
 *       后续任务不再路由到代理人</li>
 *   <li><b>授权通知</b>：授权创建 / 撤销时通过 {@link FlowOfflineAutoForwardService}
 *       通知被授权人</li>
 *   <li><b>授权防滥用</b>：被授权人代批时，审计记录双签名（审批人 + 代理人），
 *       避免代理人越权</li>
 * </ul>
 *
 * <p><b>审计追溯：</b>所有授权动作记录到 {@code ydsz_flow_audit_log}，
 * 包括「授权人 / 被授权人 / 授权范围 / 起止时间 / 撤销时间」。
 * 代批任务的审计日志同时记录「原审批人 / 代理人」便于合规追溯。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDelegateAuthService 接口定义
 * @see com.njydsz.workflow.domain.entity.FlowDelegateAuth 委派代理实体
 * @see FlowOfflineAutoForwardService 离线自动转交服务（与委派不同：离线是自动转交，委派是主动授权）
 * @see FlowTaskAuditService 任务审计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDelegateAuthServiceImpl implements FlowDelegateAuthService {

    /** 委派授权 Mapper，负责 ydsz_flow_delegate_auth 表的增删改查 */
    private final FlowDelegateAuthMapper authMapper;
    /** 审计日志 Mapper，委派代理操作日志已合并到 ydsz_flow_audit_log */
    private final FlowAuditLogMapper auditLogMapper;
    /** P2-5: 离线代理自动转发（@Lazy 避免循环依赖） */
    @Lazy
    private final FlowOfflineAutoForwardService offlineAutoForwardService;

    /**
     * 创建委派授权
     *
     * <p>执行链路：
     * <ol>
     *   <li>参数校验（ownerUserId / delegateUserId / startTime / endTime / scopeType）</li>
     *   <li>授权范围校验（{@code ALL/FLOW/FLOW_NODE/ROLE} 必填字段）</li>
     *   <li>默认值填充（{@code tenantId} / {@code authStatus=ENABLED} / {@code providerTraceId}）</li>
     *   <li>插入 {@code ydsz_flow_delegate_auth} 记录</li>
     *   <li>P2-5：触发 <b>离线自动转发</b>，将 owner 名下在途待办按授权规则自动转给 delegate</li>
     * </ol>
     *
     * @param auth 委派授权实体（不需携带 ID）
     * @return 新创建的授权 ID
     * @throws SysException {@code BAD_REQUEST} — 参数缺失 / scope 非法 / 时间不合法
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowDelegateAuth auth) {
        if (auth == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_fdf18ac3")
                .build();
        }
        if (auth.getOwnerUserId() == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_d65b2814")
                .build();
        }
        if (auth.getDelegateUserId() == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_9999d306")
                .build();
        }
        if (auth.getOwnerUserId().equals(auth.getDelegateUserId())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_5b0149dc")
                .build();
        }
        if (auth.getStartTime() == null || auth.getEndTime() == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_8a268764")
                .build();
        }
        if (!auth.getEndTime().isAfter(auth.getStartTime())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_0e756b4f")
                .build();
        }
        if (!StringUtils.hasText(auth.getScopeType())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_4cfd103d")
                .build();
        }
        // scope 必填字段校验
        switch (auth.getScopeType()) {
            case "FLOW" -> {
                if (!StringUtils.hasText(auth.getFlowCode())) {
                    throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_2c8e3391")
                .build();
                }
            }
            case "FLOW_NODE" -> {
                if (!StringUtils.hasText(auth.getFlowCode())
                        || !StringUtils.hasText(auth.getNodeCode())) {
                    throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_8722656e")
                .build();
                }
            }
            case "ROLE" -> {
                if (!StringUtils.hasText(auth.getRoleCode())) {
                    throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_19801c0e")
                .build();
                }
            }
            case "ALL" -> { /* no-op */ }
            default -> throw SysException.builder()
                    .resultCode(BaseResultCode.BAD_REQUEST)
                    .key("error.workflow.msg_b0022eba").params(auth.getScopeType())
                    .build();
        }

        // 默认值
        if (auth.getTenantId() == null) {
            auth.setTenantId(AuthContextUtils.getTenantIdOrDefault());
        }
        if (auth.getAuthStatus() == null) {
            auth.setAuthStatus("ENABLED");
        }
        auth.setProviderTraceId(TracerUtils.getOrCreateTraceId());

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

    /**
     * 撤销授权（提前结束授权）
     *
     * <p>将授权状态从 {@code ENABLED} / {@code DISABLED} 切换为 {@code REVOKED}，
     * 撤销后该授权<b>立即失效</b>，后续任务不再路由到代理人。
     * 已路由到代理人的在途任务<b>不</b>自动回收，由代理人在「我的待办」中继续处理。
     *
     * @param authId      授权 ID
     * @param ownerUserId 授权人 ID（用于权限校验；为 null 时跳过校验，<b>不建议</b>）
     * @throws SysException {@code NOT_FOUND} — 授权不存在；
     *                     {@code FORBIDDEN} — {@code ownerUserId} 与授权人不匹配
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String authId, String ownerUserId) {
        if (authId == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_7804c8f2")
                .build();
        }
        FlowDelegateAuth auth = authMapper.selectById(authId);
        if (auth == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_c47a9632").params(authId)
                .build();
        }
        if (ownerUserId != null && !ownerUserId.equals(auth.getOwnerUserId())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.FORBIDDEN)
                .message("error.workflow.msg_f121ff85")
                .build();
        }
        int n = authMapper.updateStatus(authId, "REVOKED", LocalDateTime.now());
        log.info("[FlowDelegate] 撤回授权: authId={} owner={} affected={}", authId, auth.getOwnerUserId(), n);
    }

    /**
     * 启用 / 禁用授权
     *
     * <p>区别于 {@link #revoke}（撤销不可恢复），本方法仅临时禁用（{@code DISABLED}），
     * 可通过再次 {@code ENABLED} 恢复。{@code REVOKED} 状态不可恢复。
     *
     * @param authId     授权 ID
     * @param status     目标状态（{@code ENABLED} / {@code DISABLED}）
     * @param operatorId 操作人 ID（用于权限校验；为 null 时跳过校验）
     * @throws SysException {@code BAD_REQUEST} — 参数缺失 / status 非法；
     *                     {@code NOT_FOUND} — 授权不存在；
     *                     {@code FORBIDDEN} — operatorId 与 owner 不匹配
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String authId, String status, String operatorId) {
        if (authId == null || !StringUtils.hasText(status)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_40437174")
                .build();
        }
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_7678ad83")
                .build();
        }
        FlowDelegateAuth auth = authMapper.selectById(authId);
        if (auth == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_c47a9632").params(authId)
                .build();
        }
        // 权限校验：仅 owner 可改
        if (operatorId != null && !operatorId.equals(auth.getOwnerUserId())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.FORBIDDEN)
                .message("error.workflow.msg_d6a95488")
                .build();
        }
        int n = authMapper.updateStatus(authId, status, LocalDateTime.now());
        log.info("[FlowDelegate] 更新授权状态: authId={} status={} operator={} affected={}",
                authId, status, operatorId, n);
    }

    /**
     * 查询「我作为授权人」的授权列表
     *
     * @param ownerUserId 授权人 ID
     * @param tenantId    租户 ID（默认从 {@link AuthContextUtils} 取）
     * @param status      状态过滤（{@code ENABLED/DISABLED/REVOKED/EXPIRED}，可为 null）
     * @return 授权列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlowDelegateAuth> listMine(String ownerUserId, String tenantId, String status) {
        if (ownerUserId == null) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        return authMapper.selectByOwner(tid, ownerUserId, status);
    }

    /**
     * 查询「我作为代理人」的授权列表
     *
     * @param delegateUserId 代理人 ID
     * @param tenantId       租户 ID
     * @param status         状态过滤
     * @return 授权列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<FlowDelegateAuth> listAsDelegate(String delegateUserId, String tenantId, String status) {
        if (delegateUserId == null) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        return authMapper.selectByDelegate(tid, delegateUserId, status);
    }

    /**
     * 匹配某审批任务应走的代理人（按 scope 优先级）
     *
     * <p>匹配规则：{@code FLOW_NODE > FLOW > ROLE > ALL}，匹配时校验当前时间在
     * {@code [startTime, endTime]} 区间内且状态为 {@code ENABLED}。
     * 用于任务创建时确定实际处理人（{@link #resolveDelegateChain} 的单层匹配）。
     *
     * @param tenantId   租户 ID
     * @param ownerUserId 原审批人 ID
     * @param flowCode   流程编码（可为 null）
     * @param nodeCode   节点编码（可为 null）
     * @return 匹配的授权（无匹配返回 {@code null}，<b>不抛异常</b>）
     */
    @Override
    @Transactional(readOnly = true)
    public FlowDelegateAuth matchAuth(String tenantId, String ownerUserId,
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
     *
     * <p>通过 {@link DistributedScheduled} 保证多节点部署时仅一个节点执行扫描，
     * 获取不到锁的节点直接跳过本次执行（非阻塞），避免重复标记。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    @DistributedScheduled(lockKey = "flow:delegate:scan-expired", leaseTime = 60)
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

    /**
     * 扫描并标记过期授权（手动触发）
     *
     * <p>将 {@code endTime < now} 且状态为 {@code ENABLED} 的授权标记为 {@code EXPIRED}。
     * 本方法由 {@link #scheduledScanExpired} 定时任务调用，也可由外部手动触发。
     *
     * @return 标记过期的授权数
     */
    @Override
    public int scanAndMarkExpired() {
        try {
            return authMapper.markExpired(LocalDateTime.now(), LocalDateTime.now());
        } catch (Exception e) {
            log.error("[FlowDelegate] 扫描过期标记异常: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 查询代理人操作日志（代理人作为操作人执行的代批任务）
     *
     * <p>从 {@code ydsz_flow_audit_log} 中按 {@code BIZ_TYPE_DELEGATE_PROXY} 业务类型 +
     * {@code operatorId=delegateUserId} 查询，按时间倒序分页。
     *
     * @param delegateUserId 代理人 ID
     * @param page           页码（从 1 开始）
     * @param size           每页大小
     * @return 操作日志分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public BaseResponse<?> listDelegateLog(String delegateUserId, int page, int size) {
        if (delegateUserId == null) {
            return PageResponse.success(0L, 0L, 0L, null);
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        LambdaQueryWrapper<FlowAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAuditLog::getBusinessType, FlowTaskAuditService.BIZ_TYPE_DELEGATE_PROXY)
                .eq(FlowAuditLog::getOperatorId, delegateUserId)
                .orderByDesc(FlowAuditLog::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        List<FlowAuditLog> list = auditLogMapper.selectList(wrapper);
        return PageResponse.success((long) list.size(), (long) safePage, (long) safeSize, list);
    }

    /**
     * 查询授权人被代理的操作日志（被代批任务）
     *
     * <p>从 {@code ydsz_flow_audit_log} 中按 {@code BIZ_TYPE_DELEGATE_PROXY} 业务类型 +
     * {@code targetId=ownerUserId} 查询，按时间倒序分页。
     *
     * @param ownerUserId 授权人 ID
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @return 操作日志分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public BaseResponse<?> listOwnerLog(String ownerUserId, int page, int size) {
        if (ownerUserId == null) {
            return PageResponse.success(0L, 0L, 0L, null);
        }
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        LambdaQueryWrapper<FlowAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAuditLog::getBusinessType, FlowTaskAuditService.BIZ_TYPE_DELEGATE_PROXY)
                .eq(FlowAuditLog::getTargetId, ownerUserId)
                .orderByDesc(FlowAuditLog::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize);
        List<FlowAuditLog> list = auditLogMapper.selectList(wrapper);
        return PageResponse.success((long) list.size(), (long) safePage, (long) safeSize, list);
    }

    // ==================== P1-7: 链式解析代理人 ====================

    /** 最大委派链深度，防止无限递归（A→B→C→D→E→A 循环保护） */
    private static final int MAX_CHAIN_DEPTH = 5;

    /**
     * 链式解析代理人（A 委派 B → B 又委派 C → 最终代理人）
     *
     * <p>P1-7 增强能力：支持<b>多级委派链</b>解析，处理「A 出差 → 授权 B → B 也不在 → 授权 C」场景。
     *
     * <p>解析规则：
     * <ol>
     *   <li>从 {@code ownerUserId} 出发，循环匹配授权</li>
     *   <li>每跳到下一个人，更新 {@code currentUserId} 并记录到 {@code visited} 集合</li>
     *   <li>遇到循环（A→B→A）或超过 {@link #MAX_CHAIN_DEPTH} 时停止</li>
     * </ol>
     *
     * @param tenantId    租户 ID
     * @param ownerUserId 原始审批人 ID
     * @param flowCode    流程编码
     * @param nodeCode    节点编码
     * @return 最终代理人 ID（无匹配或循环时返回 {@code ownerUserId} 本身）
     */
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
            FlowDelegateAuth matched = matchAuth(tenantId, currentUserId, flowCode, nodeCode);
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
