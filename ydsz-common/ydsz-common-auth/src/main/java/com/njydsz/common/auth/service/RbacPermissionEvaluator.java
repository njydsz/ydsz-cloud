package com.njydsz.common.auth.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.annotation.AuthMenuPermission;
import com.njydsz.common.auth.annotation.PermissionMode;
import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.constant.AuthErrorCode;
import com.njydsz.common.auth.exception.PermissionDeniedException;
import com.njydsz.common.auth.exception.PermissionDeniedException.PermissionType;
import com.njydsz.common.auth.hierarchy.PermissionHierarchyService;
import com.njydsz.common.auth.metrics.AuthMetricsCollector;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.strategy.CacheKeyStrategy;
import com.njydsz.common.auth.strategy.DefaultCacheKeyStrategy;
import com.njydsz.common.auth.util.PermissionUtils;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.string.StringUtils;

/**
 * RBAC 权限校验器。
 *
 * <p>职责边界：
 *
 * <ul>
 *   <li>根据 accessToken 加载用户信息（Map）
 *   <li>从用户信息中解析用户角色（固定字段 roleCode）
 *   <li>根据角色加载权限集合（菜单/按钮/接口）
 *   <li>按注解要求（AND/OR、权限类型）进行校验
 * </ul>
 *
 * <p><b>注意：</b>角色权限缓存由 {@link RolePermissionCacheService} 封装管理（TTL 可配置，默认 30 分钟）， 当 Redis
 * 不可用时降级到本地缓存。仅对通配符权限的正则 Pattern 做轻量缓存以避免重复编译。
 *
 * <p><b>异常说明：</b> 校验失败时抛出 {@link PermissionDeniedException}，包含完整的权限上下文信息：
 *
 * <ul>
 *   <li>userId：当前用户 ID
 *   <li>userRoles：当前用户角色
 *   <li>requiredPermissions：缺少的权限
 *   <li>grantedPermissions：已有的权限
 *   <li>resource：被访问的资源
 *   <li>checkMode：校验模式（AND/OR）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see PermissionDeniedException
 */
@Slf4j
public class RbacPermissionEvaluator {

  private final AuthProperties properties;
  private final RbacUserInfoService userInfoService;
  private final RolePermissionLoader rolePermissionLoader;
  private final RolePermissionCacheService rolePermissionCacheService;

  /** 权限层级服务，支持按租户隔离。 可选注入，为 null 时回退到 {@link PermissionUtils} 静态调用（向后兼容）。 */
  private PermissionHierarchyService hierarchyService;

  /** 可选的指标采集器，由 AuthConfiguration 注入。为 null 时不采集指标（向后兼容）。 */
  private AuthMetricsCollector metricsCollector;

  /** 缓存 Key 生成策略，默认为 DefaultCacheKeyStrategy */
  private CacheKeyStrategy cacheKeyStrategy = new DefaultCacheKeyStrategy();

  /** 标记 Redis 是否可用，供 AuthConfiguration 的健康检查更新。 */
  private volatile boolean redisAvailable = true;

  public RbacPermissionEvaluator(
      AuthProperties properties,
      RbacUserInfoService userInfoService,
      RolePermissionLoader rolePermissionLoader,
      RolePermissionCacheService rolePermissionCacheService) {
    this.properties = properties;
    this.userInfoService = userInfoService;
    this.rolePermissionLoader = rolePermissionLoader;
    this.rolePermissionCacheService = rolePermissionCacheService;
  }

  /**
   * 使用 accessToken 加载用户信息。
   *
   * @param accessToken 请求 token（通常来自请求头 X-Access-Token）
   * @return 用户信息 Map
   */
  public Map<String, Object> loadUserInfo(String accessToken) {
    if (StringUtils.isBlank(accessToken)) {
      throw BusinessException.builder().resultCode(AuthErrorCode.TOKEN_MISSING).build();
    }

    Map<String, Object> userInfo = userInfoService.loadUserInfoMap(accessToken);
    if (userInfo == null || userInfo.isEmpty()) {
      throw BusinessException.builder().resultCode(AuthErrorCode.TOKEN_EXPIRED).build();
    }
    return userInfo;
  }

  /**
   * 加载当前登录用户信息。
   *
   * <p>优先从请求级 ThreadLocal 缓存获取，缓存未命中时才从 Redis 加载， 避免同一请求内多次调用导致反复 Redis 查询（原实现每次调用均走 Redis）。
   *
   * @return 用户信息 Map
   */
  public Map<String, Object> loadCurrentUserInfo() {
    Map<String, Object> cached = RequestContext.getCachedUserInfoMap();
    if (cached != null && !cached.isEmpty()) {
      return cached;
    }
    Map<String, Object> userInfo = loadUserInfo(userInfoService.loadCurrentToken());
    RequestContext.put(BizContextKeys.KEY_CACHED_USER_INFO_MAP, userInfo);
    return userInfo;
  }

  /**
   * 校验是否满足注解要求的角色与权限。
   *
   * @param userInfo 用户信息 Map
   * @param required 目标方法/类上的 {@link AuthMenuPermission} 注解
   */
  public void validateMenu(Map<String, Object> userInfo, AuthMenuPermission required) {
    if (required == null) return;
    validateMenu0(
        userInfo,
        required.permissionCodes(),
        required.roleCodes(),
        required.type(),
        required.mode());
  }

  private void validateMenu0(
      Map<String, Object> userInfo,
      String[] permissionCodes,
      String[] roleCodes,
      AuthMenuPermission.PermissionType type,
      PermissionMode mode) {
    if (!properties.isEnabled()) return;
    Set<String> requiredRoles = arrayToSet(roleCodes);
    Set<String> requiredPerms = arrayToSet(permissionCodes);
    if (requiredRoles.isEmpty() && requiredPerms.isEmpty()) return;

    Set<String> userRoles = userInfo != null ? parseUserRoles(userInfo) : new HashSet<>();
    if (isSuperAdmin(userRoles)) return;

    PermissionType permType = mapToPermissionType(type);
    boolean orMode = mode == PermissionMode.OR;

    if (!requiredRoles.isEmpty()) {
      validateRoles(userRoles, requiredRoles, orMode, permType);
    }
    if (!requiredPerms.isEmpty()) {
      RolePermissions rp = getPermissionsByRoleCodes(userRoles);
      Set<String> userPerms = selectPermissions(rp, type);
      validatePermissions(userPerms, requiredPerms, orMode, permType, userPerms);
    }
  }

  /**
   * 校验当前用户是否满足接口权限要求。
   *
   * @param apiCodes 需要校验的接口权限码数组
   * @param roleCodes 需要校验的角色编码数组
   * @param mode 校验模式（AND/OR）
   */
  public void validateApi(String[] apiCodes, String[] roleCodes, PermissionMode mode) {
    validateApi0(loadCurrentUserInfo(), apiCodes, roleCodes, mode);
  }

  /**
   * 校验当前用户是否满足菜单/按钮权限要求。
   *
   * @param permissionCodes 需要校验的权限码数组
   * @param roleCodes 需要校验的角色编码数组
   * @param type 权限类型（菜单/按钮）
   * @param mode 校验模式（AND/OR）
   */
  public void validateMenu(
      String[] permissionCodes,
      String[] roleCodes,
      AuthMenuPermission.PermissionType type,
      PermissionMode mode) {
    validateMenu0(loadCurrentUserInfo(), permissionCodes, roleCodes, type, mode);
  }

  /**
   * 校验用户是否拥有指定接口权限。
   *
   * @param userInfo 用户信息 Map
   * @param required 接口权限注解
   */
  public void validateApi(Map<String, Object> userInfo, AuthApiPermission required) {
    if (required == null) return;
    validateApi0(userInfo, required.apiCodes(), required.roleCodes(), required.mode());
  }

  private void validateApi0(
      Map<String, Object> userInfo, String[] apiCodes, String[] roleCodes, PermissionMode mode) {
    if (!properties.isEnabled()) return;
    Set<String> requiredRoles = arrayToSet(roleCodes);
    Set<String> requiredApis = arrayToSet(apiCodes);
    if (requiredRoles.isEmpty() && requiredApis.isEmpty()) return;

    Set<String> userRoles = userInfo != null ? parseUserRoles(userInfo) : new HashSet<>();
    if (isSuperAdmin(userRoles)) return;

    boolean orMode = mode == PermissionMode.OR;

    if (!requiredRoles.isEmpty()) {
      validateRoles(userRoles, requiredRoles, orMode, PermissionType.API);
    }
    if (!requiredApis.isEmpty()) {
      RolePermissions rp = getPermissionsByRoleCodes(userRoles);
      Set<String> grantedPerms = rp.getApiPermissions();
      validatePermissions(grantedPerms, requiredApis, orMode, PermissionType.API, grantedPerms);
    }
  }

  /**
   * 设置缓存 Key 生成策略。
   *
   * <p>支持自定义缓存 Key 生成规则，默认为 {@link DefaultCacheKeyStrategy}。
   *
   * @param cacheKeyStrategy 缓存 Key 生成策略
   */
  /**
   * 设置权限层级服务。
   *
   * @param hierarchyService 权限层级服务实例
   */
  public void setHierarchyService(PermissionHierarchyService hierarchyService) {
    this.hierarchyService = hierarchyService;
  }

  /**
   * 设置指标采集器（由 AuthConfiguration 注入）。
   *
   * @param metricsCollector 指标采集器，为 null 时不采集
   */
  public void setMetricsCollector(AuthMetricsCollector metricsCollector) {
    this.metricsCollector = metricsCollector;
  }

  /** 记录权限校验通过。 */
  private void recordAllow(String permissionType) {
    if (metricsCollector != null) {
      metricsCollector.recordPermissionAllow(permissionType);
    }
  }

  /** 记录权限校验拒绝。 */
  private void recordDeny(String permissionType, String requiredPermissions, String resource) {
    if (metricsCollector != null) {
      metricsCollector.recordPermissionDeny(
          resolveUserId(), permissionType, requiredPermissions, resource);
    }
  }

  /**
   * 设置缓存 Key 生成策略。
   *
   * <p>用于自定义权限缓存键的拼装规则（如按租户/数据源拆分缓存）， 需在首次查询缓存前注入，运行期间替换会影响后续所有缓存读写。
   *
   * @param cacheKeyStrategy 缓存 Key 生成策略，不可为 {@code null}
   * @throws NullPointerException 当 {@code cacheKeyStrategy} 为 {@code null} 时抛出
   */
  public void setCacheKeyStrategy(CacheKeyStrategy cacheKeyStrategy) {
    Objects.requireNonNull(cacheKeyStrategy, "cacheKeyStrategy cannot be null");
    this.cacheKeyStrategy = cacheKeyStrategy;
  }

  /**
   * 获取当前缓存 Key 生成策略。
   *
   * @return 缓存 Key 生成策略
   */
  public CacheKeyStrategy getCacheKeyStrategy() {
    return cacheKeyStrategy;
  }

  /**
   * 标记 Redis 是否可用。
   *
   * <p>由 AuthConfiguration 的定时健康检查调用。
   *
   * @param available Redis 是否可用
   */
  public void setRedisAvailable(boolean available) {
    if (this.redisAvailable != available) {
      this.redisAvailable = available;
      if (!available) {
        log.warn(
            "[RbacPermissionEvaluator] Redis 不可用，已切换降级模式，fallbackPolicy={}",
            properties.getFallbackPolicy());
      } else {
        log.info("[RbacPermissionEvaluator] Redis 已恢复，切换回正常模式");
      }
    }
  }

  /**
   * 判断 Redis 是否可用。
   *
   * @return Redis 是否可用
   */
  public boolean isRedisAvailable() {
    return redisAvailable;
  }

  /** 清理内部缓存。 */
  public void clearAllCaches() {
    PermissionUtils.clearPatternCache();
    rolePermissionCacheService.invalidateAll();
  }

  /** 销毁时清理缓存。 */
  @PreDestroy
  public void destroy() {
    rolePermissionCacheService.invalidateAll();
    log.info("[RbacPermissionEvaluator] 缓存已清理");
  }

  /**
   * 判断当前用户是否拥有指定权限。
   *
   * <p>合并用户所有角色的菜单/按钮/接口权限，判断是否包含指定权限码。 支持通配符匹配（需启用 {@code wildcardEnabled}）。
   *
   * @param permission 权限码
   * @return 是否拥有该权限
   */
  public boolean hasPermission(String permission) {
    try {
      Map<String, Object> userInfo = loadCurrentUserInfo();
      Set<String> userRoles = parseUserRoles(userInfo);
      if (isSuperAdmin(userRoles)) {
        return true;
      }
      RolePermissions rp = getPermissionsByRoleCodes(userRoles);
      Set<String> allPerms = new HashSet<>();
      if (rp.getMenuPermissions() != null) allPerms.addAll(rp.getMenuPermissions());
      if (rp.getButtonPermissions() != null) allPerms.addAll(rp.getButtonPermissions());
      if (rp.getApiPermissions() != null) allPerms.addAll(rp.getApiPermissions());
      return hasPermission(allPerms, permission);
    } catch (Exception e) {
      log.error(
          "[RbacPermissionEvaluator] 权限校验异常，Redis 连接失败导致权限检查失败, permission={}, fallbackPolicy={}",
          permission,
          properties.getFallbackPolicy(),
          e);
      return false;
    }
  }

  private RolePermissions getPermissionsByRoleCodes(Set<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return RolePermissions.empty();
    }

    String tenantId = resolveTenantId();
    String cacheKey = cacheKeyStrategy.generate(tenantId, roleCodes);
    RolePermissions cached = rolePermissionCacheService.getCachedPermissions(cacheKey);
    if (cached != null) {
      return cached;
    }

    Set<String> menu = new HashSet<>();
    Set<String> button = new HashSet<>();
    Set<String> api = new HashSet<>();

    // 使用批量加载（Redis Pipeline）替代逐个加载，将 2N 次 GET 合并为 1 次往返
    try {
      Map<String, RolePermissions> permissionsMap = rolePermissionLoader.loadByRoleCodes(roleCodes);
      for (RolePermissions single : permissionsMap.values()) {
        if (single == null) {
          continue;
        }
        if (single.getMenuPermissions() != null) {
          menu.addAll(single.getMenuPermissions());
        }
        if (single.getButtonPermissions() != null) {
          button.addAll(single.getButtonPermissions());
        }
        if (single.getApiPermissions() != null) {
          api.addAll(single.getApiPermissions());
        }
      }
    } catch (Exception e) {
      // Redis 不可用时降级：记录日志并继续，仅当 Redis 可用时才放入缓存避免缓存毒化
      log.warn(
          "[RbacPermissionEvaluator] 批量加载角色权限失败，redisAvailable={}, fallbackPolicy={}: {}",
          redisAvailable,
          properties.getFallbackPolicy(),
          e.getMessage());
    }

    RolePermissions result =
        new RolePermissions(
            Collections.unmodifiableSet(menu),
            Collections.unmodifiableSet(button),
            Collections.unmodifiableSet(api));

    // 仅在 Redis 可用时放入缓存，避免 Redis 故障期间的空权限被缓存毒化
    if (redisAvailable) {
      rolePermissionCacheService.cachePermissions(cacheKey, roleCodes, result);
    }

    return result;
  }

  private String resolveTenantId() {
    // 优先从 ThreadLocal 缓存获取，避免同一次请求内多次 Redis 查询
    String cached = RequestContext.getTenantId();
    if (cached != null) {
      return cached;
    }
    try {
      // loadCurrentUserInfo() 已有请求级缓存，不会重复查 Redis
      Map<String, Object> userInfo = loadCurrentUserInfo();
      if (userInfo != null) {
        Object tenantId = userInfo.get("tenantId");
        if (tenantId != null) {
          String tid = String.valueOf(tenantId);
          RequestContext.setTenantId(tid);
          return tid;
        }
      }
    } catch (Exception e) {
      log.debug("解析 tenantId 异常，将返回 null: {}", e.getMessage());
    }
    return null;
  }

  /**
   * 按角色清理缓存。
   *
   * <p>由于缓存 Key 使用 SHA-256 Hash，无法从 Key 反解角色， 通过维护的 roleCode → cacheKey 反向索引进行精确清理。
   */
  public void clearCachesByRoleCodes(String csvRoleCodes) {
    PermissionUtils.clearPatternCache();
    if (csvRoleCodes != null && !csvRoleCodes.isEmpty()) {
      Set<String> roleCodes = PermissionUtils.splitCsv(csvRoleCodes);
      rolePermissionCacheService.invalidateByRoleCodes(roleCodes);
    } else {
      rolePermissionCacheService.invalidateAll();
    }
  }

  private Set<String> selectPermissions(
      RolePermissions rolePermissions, AuthMenuPermission.PermissionType type) {
    if (rolePermissions == null) {
      return Collections.emptySet();
    }
    switch (type) {
      case MENU:
        return rolePermissions.getMenuPermissions();
      case BUTTON:
      default:
        return rolePermissions.getButtonPermissions();
    }
  }

  private boolean isSuperAdmin(Set<String> userRoles) {
    return PermissionUtils.isSuperAdmin(userRoles, properties.getIgnoreRoles());
  }

  private void validateRoles(
      Set<String> userRoles, Set<String> requiredRoles, boolean orMode, PermissionType type) {
    MDC.put("permission.type", type.name());
    MDC.put("permission.checkMode", orMode ? "OR" : "AND");
    try {
      boolean ok;
      if (orMode) {
        ok = userRoles.stream().anyMatch(requiredRoles::contains);
      } else {
        ok = userRoles.containsAll(requiredRoles);
      }
      if (!ok) {
        recordDeny(type.name(), requiredRoles.toString(), resolveCurrentResource());
        throw PermissionDeniedException.denied()
            .userId(resolveUserId())
            .userRoles(userRoles)
            .requiredPermissions(requiredRoles)
            .permissionType(type)
            .resource(resolveCurrentResource())
            .checkMode(orMode ? "OR" : "AND")
            .build();
      }
      recordAllow(type.name());
    } finally {
      MDC.remove("permission.type");
      MDC.remove("permission.checkMode");
    }
  }

  private void validatePermissions(
      Set<String> grantedPerms,
      Set<String> requiredPerms,
      boolean orMode,
      PermissionType type,
      Set<String> userGrantedPerms) {
    MDC.put("permission.type", type.name());
    MDC.put("permission.checkMode", orMode ? "OR" : "AND");
    try {
      Set<String> matchedPermissions = new HashSet<>();
      Set<String> missingPermissions = new HashSet<>();

      for (String required : requiredPerms) {
        boolean found = hasPermission(grantedPerms, required);
        if (found) {
          matchedPermissions.add(required);
        } else {
          missingPermissions.add(required);
        }
      }

      boolean ok;
      if (orMode) {
        ok = !matchedPermissions.isEmpty();
      } else {
        ok = missingPermissions.isEmpty();
      }

      if (!ok) {
        recordDeny(type.name(), missingPermissions.toString(), resolveCurrentResource());
        throw PermissionDeniedException.denied()
            .userId(resolveUserId())
            .userRoles(parseUserRoles(loadCurrentUserInfo()))
            .requiredPermissions(requiredPerms)
            .grantedPermissions(userGrantedPerms)
            .permissionType(type)
            .resource(resolveCurrentResource())
            .checkMode(orMode ? "OR" : "AND")
            .build();
      }
      recordAllow(type.name());
    } finally {
      MDC.remove("permission.type");
      MDC.remove("permission.checkMode");
    }
  }

  private boolean hasPermission(Set<String> granted, String required) {
    if (hierarchyService != null) {
      return hierarchyService.hasPermission(
          resolveTenantIdOrDefault(), granted, required, properties.isWildcardEnabled());
    }
    // 向后兼容：回退到 PermissionUtils 静态调用
    return PermissionUtils.hasPermission(granted, required, properties.isWildcardEnabled());
  }

  /**
   * 解析当前租户 ID，如果无法解析则返回默认租户 ID。
   *
   * @return 租户 ID（不会返回 null）
   */
  private String resolveTenantIdOrDefault() {
    String tenantId = resolveTenantId();
    return tenantId != null ? tenantId : PermissionHierarchyService.DEFAULT_TENANT_ID;
  }

  private Set<String> parseUserRoles(Map<String, Object> userInfo) {
    if (userInfo == null) {
      return Collections.emptySet();
    }
    Object v = userInfo.get(resolveRoleCodeField());
    if (v == null) {
      return Collections.emptySet();
    }
    return PermissionUtils.splitCsv(String.valueOf(v));
  }

  private String resolveRoleCodeField() {
    String roleCodeField = properties.getRoleCodeField();
    if (StringUtils.isBlank(roleCodeField)) {
      return "roleCode";
    }
    return roleCodeField.trim();
  }

  private String resolveUserId() {
    try {
      Map<String, Object> userInfo = loadCurrentUserInfo();
      if (userInfo != null) {
        Object userId = userInfo.get("userId");
        if (userId != null) {
          return String.valueOf(userId);
        }
      }
    } catch (Exception e) {
      log.debug("[RbacPermissionEvaluator] 解析当前用户ID失败: {}", e.getMessage());
    }
    return null;
  }

  private String resolveCurrentResource() {
    try {
      Object request = RequestContext.get(BizContextKeys.KEY_HTTP_REQUEST);
      if (request instanceof HttpServletRequest) {
        return ((HttpServletRequest) request).getRequestURI();
      }
    } catch (Exception e) {
      log.debug("[RbacPermissionEvaluator] 解析当前资源路径失败: {}", e.getMessage());
    }
    return null;
  }

  private Set<String> arrayToSet(String[] arr) {
    if (arr == null || arr.length == 0) {
      return Collections.emptySet();
    }
    return Arrays.stream(arr)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toSet());
  }

  private PermissionType mapToPermissionType(AuthMenuPermission.PermissionType type) {
    if (type == null) {
      return PermissionType.MENU;
    }
    switch (type) {
      case MENU:
        return PermissionType.MENU;
      case BUTTON:
        return PermissionType.BUTTON;
      default:
        return PermissionType.MENU;
    }
  }
}
