package com.njydsz.system.server.service.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.service.PermissionFallbackProvider;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.system.domain.repository.ApiPermissionRepository;
import com.njydsz.system.domain.vo.ApiPermissionVO;

/**
 * 系统模块权限降级提供者实现。
 *
 * <p>当 Redis 不可用时，{@link PermissionFallbackProvider} 通过本实现从 DB 的 {@code sys_api_permission} 表
 * 查询租户下已注册的 API 权限码，为超级管理员角色返回所有权限，其他角色返回空集合。
 *
 * <p>注册为 Spring Bean 后，会被 ydzsz-common-auth 的 RbacPermissionEvaluator 通过
 * {@code @Autowired(required = false)} 自动注入（要求 classpath 中有此实现）。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see PermissionFallbackProvider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemPermissionFallbackProvider implements PermissionFallbackProvider {

  /** 超级管理员角色编码 */
  private static final Set<String> SUPER_ADMIN_ROLES = Set.of("admin", "super_admin");

  private final ApiPermissionRepository apiPermissionRepository;

  @Override
  public Set<String> getRoleApiCodes(String roleCode, String tenantId) {
    if (roleCode == null || roleCode.isBlank()) {
      return Collections.emptySet();
    }

    // 仅超级管理员返回全部 API 权限
    if (!SUPER_ADMIN_ROLES.contains(roleCode)) {
      log.debug(I18n.message("system.fallback.non_super_admin", new Object[]{roleCode}));
      return Collections.emptySet();
    }

    try {
      List<ApiPermissionVO> permissions = apiPermissionRepository.listAllByTenant(tenantId);
      if (permissions == null || permissions.isEmpty()) {
        log.warn(I18n.message("system.fallback.no_api_permission", new Object[]{tenantId}));
        return Collections.emptySet();
      }
      Set<String> apiCodes = new HashSet<>(permissions.size());
      for (ApiPermissionVO vo : permissions) {
        if (vo.getApiCode() != null && !vo.getApiCode().isBlank()) {
          apiCodes.add(vo.getApiCode());
        }
      }
      log.info(
          I18n.message("system.fallback.completed", new Object[]{roleCode, tenantId, apiCodes.size()}));
      return apiCodes;
    } catch (Exception e) {
      log.error(
          I18n.message("system.fallback.query_failed", new Object[]{roleCode, tenantId, e.getMessage()}),
          e);
      return Collections.emptySet();
    }
  }
}
