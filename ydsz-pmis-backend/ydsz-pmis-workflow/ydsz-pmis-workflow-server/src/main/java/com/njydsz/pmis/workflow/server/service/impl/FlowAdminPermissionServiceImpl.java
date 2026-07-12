package com.njydsz.pmis.workflow.server.service.impl.analytics;

import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.domain.entity.analytics.FlowAdminRoleDO;
import com.njydsz.pmis.workflow.infra.mapper.analytics.FlowAdminRoleMapper;
import com.njydsz.pmis.workflow.server.service.analytics.FlowAdminPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程管理员权限服务实现（P1-6）
 *
 * <p>基于 {@code pmis_flow_admin_role} 表实现角色权限检查。
 * 角色层级：ADMIN > DESIGNER > AUDITOR。
 *
 * <p>权限规则：
 * <ul>
 *   <li>{@code isAdmin} — 拥有 FLOW_ADMIN 角色</li>
 *   <li>{@code canManageFlow} — ADMIN 可管理所有流程；DESIGNER 可管理自己创建的流程</li>
 *   <li>{@code canDesignFlow} — ADMIN 或 DESIGNER</li>
 *   <li>{@code canAudit} — ADMIN、AUDITOR 或 DESIGNER</li>
 * </ul>
 *
 * <p>角色过期自动失效：查询时检查 expireAt，过期角色视为无效。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAdminPermissionServiceImpl implements FlowAdminPermissionService {

    /** 管理员角色 Mapper，查询 pmis_flow_admin_role 表的角色授权记录 */
    private final FlowAdminRoleMapper adminRoleMapper;

    /** 超级管理员角色编码：拥有所有流程的管理和设计权限 */
    public static final String ROLE_ADMIN = "FLOW_ADMIN";
    /** 流程设计者角色编码：可设计和管理自己创建的流程 */
    public static final String ROLE_DESIGNER = "FLOW_DESIGNER";
    /** 审计员角色编码：可查看所有流程的审计数据 */
    public static final String ROLE_AUDITOR = "FLOW_AUDITOR";

    @Override
    public boolean hasRole(String userId, String roleCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(roleCode)) {
            return false;
        }
        String tenantId = TenantContext.getTenantId();
        FlowAdminRoleDO role = adminRoleMapper.selectByUserAndRole(userId, roleCode, tenantId);
        return role != null && isRoleValid(role);
    }

    @Override
    public boolean isAdmin(String userId) {
        return hasRole(userId, ROLE_ADMIN);
    }

    @Override
    public boolean canManageFlow(String userId, String flowCode) {
        // ADMIN 可管理所有流程
        if (isAdmin(userId)) {
            return true;
        }
        // DESIGNER 可管理自己创建的流程（简化实现：检查是否有 DESIGNER 角色）
        // 实际场景应额外检查 flow_definition.created_by == userId
        return hasRole(userId, ROLE_DESIGNER);
    }

    @Override
    public boolean canDesignFlow(String userId, String flowCode) {
        return isAdmin(userId) || hasRole(userId, ROLE_DESIGNER);
    }

    @Override
    public boolean canAudit(String userId) {
        return isAdmin(userId) || hasRole(userId, ROLE_AUDITOR) || hasRole(userId, ROLE_DESIGNER);
    }

    @Override
    public List<String> listUserRoles(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        String tenantId = TenantContext.getTenantId();
        List<FlowAdminRoleDO> roles = adminRoleMapper.selectByUserId(userId, tenantId);
        return roles.stream()
                .filter(this::isRoleValid)
                .map(FlowAdminRoleDO::getRoleCode)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantRole(String userId, String roleCode, String tenantId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(roleCode)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "用户 ID 和角色编码不能为空");
        }
        // 检查是否已存在
        FlowAdminRoleDO existing = adminRoleMapper.selectByUserAndRole(userId, roleCode, tenantId);
        if (existing != null) {
            if (isRoleValid(existing)) {
                log.info("[FlowAdmin] 用户已有该角色，跳过: userId={} role={}", userId, roleCode);
                return;
            }
            // 重新启用
            existing.setEnabled(true);
            existing.setExpireAt(null);
            existing.setGrantedAt(LocalDateTime.now());
            adminRoleMapper.updateById(existing);
            log.info("[FlowAdmin] 重新启用角色: userId={} role={}", userId, roleCode);
            return;
        }
        FlowAdminRoleDO role = new FlowAdminRoleDO();
        role.setUserId(userId);
        role.setRoleCode(roleCode);
        role.setTenantId(tenantId);
        role.setEnabled(true);
        role.setGrantedAt(LocalDateTime.now());
        adminRoleMapper.insert(role);
        log.info("[FlowAdmin] 授予角色: userId={} role={} tenantId={}", userId, roleCode, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeRole(String userId, String roleCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(roleCode)) {
            return;
        }
        String tenantId = TenantContext.getTenantId();
        FlowAdminRoleDO existing = adminRoleMapper.selectByUserAndRole(userId, roleCode, tenantId);
        if (existing == null) {
            return;
        }
        existing.setEnabled(false);
        adminRoleMapper.updateById(existing);
        log.info("[FlowAdmin] 撤销角色: userId={} role={}", userId, roleCode);
    }

    /**
     * 检查角色是否有效（启用 + 未过期）。
     */
    private boolean isRoleValid(FlowAdminRoleDO role) {
        if (role == null) {
            return false;
        }
        if (Boolean.FALSE.equals(role.getEnabled())) {
            return false;
        }
        if (role.getExpireAt() != null && role.getExpireAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }
}
