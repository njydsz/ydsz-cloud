package com.njydsz.common.auth.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.ColumnScopeInfo;
import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.auth.service.ColumnScopeFallbackLoader;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.util.string.StringUtils;

/**
 * 基于本地缓存的列权限解析器实现。
 *
 * <p>当 Redis 不可用时作为兜底实现，使用 {@code ydsz-common-cache} 本地缓存存储列权限可见/可编辑规则。
 *
 * <p><b>数据来源：</b>
 *
 * <ul>
 *   <li>本地缓存命中：直接返回缓存结果
 *   <li>缓存未命中时：通过 {@link ColumnScopeFallbackLoader} SPI 从兜底数据源（DB / 配置文件）加载
 *   <li>无兜底数据源或加载失败：返回空权限范围，不阻塞请求链路
 * </ul>
 *
 * <p><b>与 Redis 实现的差异：</b>
 *
 * <ul>
 *   <li>不依赖 {@code RedisStringOps}，可在无 Redis 环境中正常工作
 *   <li>相比 {@link RedisRoleColumnPermissionResolver} 省去 JSON 解析步骤（SPI 返回结构化对象）
 *   <li>缓存 TTL 使用较短的兜底值（{@code localPermissionCacheMinutes}），避免使用过期数据
 * </ul>
 *
 * <p><b>线程安全：</b>内部 {@link Cache} 实例线程安全，可并发调用。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ColumnPermissionResolver
 * @see ColumnScopeFallbackLoader
 * @see YdszCache
 */
public class LocalRoleColumnPermissionResolver implements ColumnPermissionResolver {

  private static final Logger LOG =
      LoggerFactory.getLogger(LocalRoleColumnPermissionResolver.class);

  private final AuthProperties properties;
  private final ObjectProvider<RbacUserInfoService> userInfoServiceProvider;
  private final ObjectProvider<ColumnScopeFallbackLoader> fallbackLoaderProvider;
  private final Cache<String, ColumnScopeInfo> cache;

  public LocalRoleColumnPermissionResolver(
      AuthProperties properties,
      ObjectProvider<RbacUserInfoService> userInfoServiceProvider,
      ObjectProvider<ColumnScopeFallbackLoader> fallbackLoaderProvider) {
    this.properties = properties;
    this.userInfoServiceProvider = userInfoServiceProvider;
    this.fallbackLoaderProvider = fallbackLoaderProvider;
    this.cache = buildCache();
  }

  private Cache<String, ColumnScopeInfo> buildCache() {
    Integer ttlSeconds = properties.getLocalPermissionCacheMinutes();
    if (ttlSeconds == null || ttlSeconds <= 0) {
      return YdszCache.<String, ColumnScopeInfo>newBuilder()
          .maximumSize(properties.getPermissionCacheMaxSize())
          .build();
    }
    return YdszCache.<String, ColumnScopeInfo>newBuilder()
        .type(CacheType.STRIPED)
        .maximumSize(properties.getPermissionCacheMaxSize())
        .expireAfterWrite(ttlSeconds, TimeUnit.MINUTES)
        .removalListener(
            (String key, ColumnScopeInfo value, RemovalCause cause) -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug("本地列权限缓存淘汰: roleCode={}, cause={}", key, cause);
              }
            })
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public ColumnScopeInfo resolve() {
    RbacUserInfoService userInfoService = userInfoServiceProvider.getIfAvailable();
    if (userInfoService == null) {
      LOG.trace("LocalRoleColumnPermissionResolver: 无 RbacUserInfoService 可用，返回空列权限");
      return ColumnScopeInfo.empty();
    }
    String token = userInfoService.loadCurrentToken();
    if (StringUtils.isBlank(token)) {
      return ColumnScopeInfo.empty();
    }
    Map<String, Object> userInfo = userInfoService.loadUserInfoMap(token);
    if (userInfo == null || userInfo.isEmpty()) {
      return ColumnScopeInfo.empty();
    }
    Set<String> roleCodes = parseUserRoles(userInfo);
    if (roleCodes.isEmpty()) {
      return ColumnScopeInfo.empty();
    }
    return resolveByRoles(roleCodes);
  }

  /**
   * 根据角色编码集合解析列权限信息。
   *
   * <p>遍历每个角色编码，从缓存或兜底数据源加载对应的列权限规则，合并所有角色的可见/可编辑字段集合。
   *
   * @param roleCodes 角色编码集合
   * @return 合并后的列权限信息，无权限时返回空的 {@link ColumnScopeInfo}
   */
  public ColumnScopeInfo resolveByRoles(Set<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return ColumnScopeInfo.empty();
    }
    Map<String, Set<String>> visibleColumnsByTable = new LinkedHashMap<>(16);
    Map<String, Set<String>> editableColumnsByTable = new LinkedHashMap<>(16);
    for (String roleCode : roleCodes) {
      ColumnScopeInfo scopeInfo = cache.getIfPresent(roleCode);
      if (scopeInfo == null) {
        scopeInfo = loadFromFallback(roleCode);
        if (scopeInfo != null && !scopeInfo.isEmpty()) {
          cache.put(roleCode, scopeInfo);
        }
      }
      mergeRules(
          visibleColumnsByTable, scopeInfo == null ? null : scopeInfo.getVisibleColumnsByTable());
      mergeRules(
          editableColumnsByTable, scopeInfo == null ? null : scopeInfo.getEditableColumnsByTable());
    }
    if (visibleColumnsByTable.isEmpty() && editableColumnsByTable.isEmpty()) {
      return ColumnScopeInfo.empty();
    }
    return new ColumnScopeInfo(
        freezeRules(visibleColumnsByTable), freezeRules(editableColumnsByTable));
  }

  private ColumnScopeInfo loadFromFallback(String roleCode) {
    ColumnScopeFallbackLoader loader = fallbackLoaderProvider.getIfAvailable();
    if (loader == null) {
      LOG.trace("LocalRoleColumnPermissionResolver: 无 ColumnScopeFallbackLoader，跳过兜底加载 roleCode={}", roleCode);
      return null;
    }
    try {
      return loader.loadByRoleCode(roleCode);
    } catch (Exception e) {
      LOG.warn("LocalRoleColumnPermissionResolver: 兜底加载列权限失败, roleCode={}, error={}",
          roleCode, e.getMessage());
      return ColumnScopeInfo.empty();
    }
  }

  private void mergeRules(Map<String, Set<String>> target, Map<String, Set<String>> source) {
    if (source == null || source.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
      if (StringUtils.isBlank(entry.getKey())
          || entry.getValue() == null
          || entry.getValue().isEmpty()) {
        continue;
      }
      target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
    }
  }

  private Map<String, Set<String>> freezeRules(Map<String, Set<String>> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Set<String>> out = new LinkedHashMap<>(16);
    for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
      out.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
    }
    return Collections.unmodifiableMap(out);
  }

  private Set<String> parseUserRoles(Map<String, Object> userInfo) {
    Object value = userInfo.get(resolveRoleCodeField());
    if (value == null) {
      return Collections.emptySet();
    }
    return Arrays.stream(String.valueOf(value).split(","))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String resolveRoleCodeField() {
    String roleCodeField = properties.getRoleCodeField();
    return StringUtils.isBlank(roleCodeField) ? "roleCode" : roleCodeField.trim();
  }
}
