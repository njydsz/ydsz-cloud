package com.njydsz.common.auth.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.DataScopeInfo;
import com.njydsz.common.auth.service.DataPermissionResolver;
import com.njydsz.common.auth.service.DataScopeFallbackLoader;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.util.string.StringUtils;

/**
 * 基于本地缓存的行级数据权限解析器实现。
 *
 * <p>当 Redis 不可用时作为兜底实现，使用 {@code ydsz-common-cache} 本地缓存存储数据权限范围信息。
 *
 * <p><b>数据来源：</b>
 *
 * <ul>
 *   <li>本地缓存命中：直接返回缓存结果
 *   <li>缓存未命中时：通过 {@link DataScopeFallbackLoader} SPI 从兜底数据源（DB / 配置文件）加载
 *   <li>无兜底数据源或加载失败：返回空权限范围，不阻塞请求链路
 * </ul>
 *
 * <p><b>与 Redis 实现的差异：</b>
 *
 * <ul>
 *   <li>不依赖 {@code RedisStringOps}，可在无 Redis 环境中正常工作
 *   <li>加载数据源由 {@link DataScopeFallbackLoader} SPI 提供
 *   <li>缓存 TTL 使用较短的兜底值（{@code localPermissionCacheMinutes}），避免使用过期的权限数据
 * </ul>
 *
 * <p><b>线程安全：</b>内部 {@link Cache} 实例线程安全，可并发调用。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DataPermissionResolver
 * @see DataScopeFallbackLoader
 * @see YdszCache
 */
public class LocalRoleDataPermissionResolver implements DataPermissionResolver {

  private static final Logger LOG =
      LoggerFactory.getLogger(LocalRoleDataPermissionResolver.class);

  private final AuthProperties properties;
  private final ObjectProvider<RbacUserInfoService> userInfoServiceProvider;
  private final ObjectProvider<DataScopeFallbackLoader> fallbackLoaderProvider;
  private final Cache<String, DataScopeInfo> cache;

  public LocalRoleDataPermissionResolver(
      AuthProperties properties,
      ObjectProvider<RbacUserInfoService> userInfoServiceProvider,
      ObjectProvider<DataScopeFallbackLoader> fallbackLoaderProvider) {
    this.properties = properties;
    this.userInfoServiceProvider = userInfoServiceProvider;
    this.fallbackLoaderProvider = fallbackLoaderProvider;
    this.cache = buildCache();
  }

  private Cache<String, DataScopeInfo> buildCache() {
    Integer ttlSeconds = properties.getLocalPermissionCacheMinutes();
    if (ttlSeconds == null || ttlSeconds <= 0) {
      return YdszCache.<String, DataScopeInfo>newBuilder()
          .maximumSize(properties.getPermissionCacheMaxSize())
          .build();
    }
    return YdszCache.<String, DataScopeInfo>newBuilder()
        .type(CacheType.STRIPED)
        .maximumSize(properties.getPermissionCacheMaxSize())
        .expireAfterWrite(ttlSeconds, TimeUnit.MINUTES)
        .removalListener(
            (String key, DataScopeInfo value, RemovalCause cause) -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug("本地数据权限缓存淘汰: roleCode={}, cause={}", key, cause);
              }
            })
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public DataScopeInfo resolve() {
    RbacUserInfoService userInfoService = userInfoServiceProvider.getIfAvailable();
    if (userInfoService == null) {
      LOG.trace("LocalRoleDataPermissionResolver: 无 RbacUserInfoService 可用，返回空数据权限");
      return DataScopeInfo.empty();
    }
    String token = userInfoService.loadCurrentToken();
    if (StringUtils.isBlank(token)) {
      return DataScopeInfo.empty();
    }
    Map<String, Object> userInfo = userInfoService.loadUserInfoMap(token);
    if (userInfo == null || userInfo.isEmpty()) {
      return DataScopeInfo.empty();
    }
    Set<String> roles = parseUserRoles(userInfo);
    if (roles.isEmpty()) {
      return DataScopeInfo.empty();
    }
    return resolveByRoles(roles);
  }

  /** {@inheritDoc} */
  @Override
  public DataScopeInfo resolveByUserInfo(Map<String, Object> userInfo) {
    Set<String> roles = parseUserRoles(userInfo);
    if (roles.isEmpty()) {
      return DataScopeInfo.empty();
    }
    return resolveByRoles(roles);
  }

  /** {@inheritDoc} */
  @Override
  public DataScopeInfo resolveByRoles(Set<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return DataScopeInfo.empty();
    }
    List<DataScopeInfo> all = new ArrayList<>(16);
    List<String> uncachedRoles = new ArrayList<>(roleCodes.size());
    for (String role : roleCodes) {
      DataScopeInfo cached = cache.getIfPresent(role);
      if (cached != null) {
        all.add(cached);
      } else {
        uncachedRoles.add(role);
      }
    }
    if (!uncachedRoles.isEmpty()) {
      for (String role : uncachedRoles) {
        DataScopeInfo loaded = loadFromFallback(role);
        if (loaded != null) {
          cache.put(role, loaded);
          all.add(loaded);
        }
      }
    }
    if (all.isEmpty()) {
      return DataScopeInfo.empty();
    }
    return mergeDataScopeInfoList(all);
  }

  private DataScopeInfo loadFromFallback(String roleCode) {
    DataScopeFallbackLoader loader = fallbackLoaderProvider.getIfAvailable();
    if (loader == null) {
      LOG.trace("LocalRoleDataPermissionResolver: 无 DataScopeFallbackLoader，跳过兜底加载 roleCode={}", roleCode);
      return null;
    }
    try {
      return loader.loadByRoleCode(roleCode);
    } catch (Exception e) {
      LOG.warn("LocalRoleDataPermissionResolver: 兜底加载数据权限失败, roleCode={}, error={}",
          roleCode, e.getMessage());
      return null;
    }
  }

  private DataScopeInfo mergeDataScopeInfoList(List<DataScopeInfo> all) {
    Set<String> companies = new HashSet<>(16);
    Set<String> depts = new HashSet<>(16);
    Set<String> projects = new HashSet<>(16);
    Set<String> regions = new HashSet<>(16);
    Set<String> spaceIds = new HashSet<>(16);
    String maxScope = null;
    String tenantId = null;
    String userId = null;
    StringBuilder customSqlConditions = new StringBuilder();
    for (DataScopeInfo info : all) {
      if (info.getCompanyIds() != null) {
        companies.addAll(info.getCompanyIds());
      }
      if (info.getDeptIds() != null) {
        depts.addAll(info.getDeptIds());
      }
      if (info.getProjectIds() != null) {
        projects.addAll(info.getProjectIds());
      }
      if (info.getRegionIds() != null) {
        regions.addAll(info.getRegionIds());
      }
      if (info.getSpaceIds() != null) {
        spaceIds.addAll(info.getSpaceIds());
      }
      if (tenantId == null) {
        tenantId = trimToNull(info.getTenantId());
      }
      if (userId == null) {
        userId = trimToNull(info.getUserId());
      }
      maxScope = info.getScope() != null ? info.getScope() : maxScope;
      if (info.hasCustomSqlCondition()) {
        String condition = info.resolveCustomSqlCondition();
        if (condition != null && !condition.isEmpty()) {
          if (customSqlConditions.length() > 0) {
            customSqlConditions.append(" OR ");
          }
          customSqlConditions.append("(").append(condition).append(")");
        }
      }
    }
    String mergedCustomCondition =
        customSqlConditions.length() > 0 ? customSqlConditions.toString() : null;
    return new DataScopeInfo(
        maxScope,
        tenantId,
        userId,
        Collections.unmodifiableSet(companies),
        Collections.unmodifiableSet(depts),
        Collections.unmodifiableSet(projects),
        Collections.unmodifiableSet(regions),
        Collections.unmodifiableSet(spaceIds),
        mergedCustomCondition,
        null);
  }

  private String trimToNull(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return value.trim();
  }

  private Set<String> parseUserRoles(Map<String, Object> userInfo) {
    Object v = userInfo.get(properties.getRoleCodeField());
    if (v == null) {
      return Collections.emptySet();
    }
    return Arrays.stream(String.valueOf(v).split(","))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** {@inheritDoc} */
  @Override
  public void invalidate(String roleCode) {
    if (StringUtils.isNotBlank(roleCode)) {
      cache.invalidate(roleCode.trim());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void invalidateAll() {
    cache.invalidateAll();
  }

  /** {@inheritDoc} */
  @Override
  public Cache<String, DataScopeInfo> getCache() {
    return cache;
  }
}
