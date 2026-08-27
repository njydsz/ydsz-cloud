package com.njydsz.workflow.server.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.workflow.domain.gateway.NameServiceClient;
import com.njydsz.workflow.server.config.FlowProperties;

/**
 * 用户/组织信息本地缓存服务。
 *
 * <p>P1: 缓存用户名称、组织名称等基础信息，避免每次审批人解析都发起 RPC 调用。
 * 使用 YdszCache 本地缓存（TinyLFU 算法），TTL 可配置（默认 15 分钟）。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>缓存 key：{@code userName:{userId}} / {@code userNames:{hash}}
 *   <li>TTL：{@code ydsz.flow.user-cache.ttl-minutes}（默认 15 分钟）
 *   <li>容量：{@code ydsz.flow.user-cache.max-size}（默认 5000 条）
 *   <li>失效：被动过期 + 主动 evict（用户信息变更时）
 * </ul>
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>通过 domain 层 {@link NameServiceClient}
 * 网关接口获取用户信息，server 层缓存实现与网关接口解耦。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowUserCacheService {

  /** 用户名称缓存 key 前缀 */
  private static final String KEY_USER_NAME = "userName:";

  /** 批量用户名称缓存 key 前缀 */
  private static final String KEY_USER_NAMES = "userNames:";

  /** 缓存缺失标记（防止缓存穿透） */
  private static final String NULL_PLACEHOLDER = "__NULL__";

  private final NameServiceClient nameServiceClient;

  /** 用户名称缓存：userId → userName */
  private final Cache<String, String> userNameCache;

  public FlowUserCacheService(NameServiceClient nameServiceClient, FlowProperties properties) {
    this.nameServiceClient = nameServiceClient;
    this.userNameCache =
        YdszCache.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .name("flow:user-name")
            .expireAfterWrite(properties.getUserCache().getTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getUserCache().getMaxSize())
            .build();
  }

  /**
   * 获取用户名称（带缓存）。
   *
   * <p>先查缓存，未命中时调用 {@link NameServiceClient#getUserName} 并回填缓存。
   * 用户不存在时缓存 NULL_PLACEHOLDER 防止穿透。
   *
   * @param userId 用户 ID
   * @return 用户名称，未找到返回 null
   */
  public String getUserName(String userId) {
    if (userId == null || userId.isBlank()) {
      return null;
    }
    String cacheKey = KEY_USER_NAME + userId;
    try {
      String cached = userNameCache.getIfPresent(cacheKey);
      if (cached != null) {
        return NULL_PLACEHOLDER.equals(cached) ? null : cached;
      }
      String name = nameServiceClient.getUserName(userId);
      userNameCache.put(cacheKey, name != null ? name : NULL_PLACEHOLDER);
      return name;
    } catch (Exception e) {
      log.warn("[FlowUserCache] 获取用户名称失败 userId={}: {}", userId, e.getMessage());
      return null;
    }
  }

  /**
   * 批量获取用户名称（带缓存）。
   *
   * <p>先批量查缓存，未命中的 ID 再批量 RPC 查询，最后回填缓存。
   *
   * @param userIds 用户 ID 列表
   * @return 用户 ID → 用户名称映射（不包含未找到的用户）
   */
  public Map<String, String> getUserNames(List<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new HashMap<>(userIds.size());
    List<String> missedIds = new ArrayList<>();

    // 1. 批量查缓存
    for (String userId : userIds) {
      if (userId == null || userId.isBlank()) {
        continue;
      }
      String cacheKey = KEY_USER_NAME + userId;
      String cached = userNameCache.getIfPresent(cacheKey);
      if (cached != null) {
        if (!NULL_PLACEHOLDER.equals(cached)) {
          result.put(userId, cached);
        }
      } else {
        missedIds.add(userId);
      }
    }

    // 2. 批量 RPC 查询未命中项
    if (!missedIds.isEmpty()) {
      try {
        Map<String, String> fetched = nameServiceClient.getUserNames(missedIds);
        if (fetched != null) {
          for (Map.Entry<String, String> entry : fetched.entrySet()) {
            String userId = entry.getKey();
            String name = entry.getValue();
            result.put(userId, name);
            userNameCache.put(KEY_USER_NAME + userId, name != null ? name : NULL_PLACEHOLDER);
          }
        }
        // 未查到的 ID 也缓存 NULL_PLACEHOLDER 防穿透
        for (String missedId : missedIds) {
          if (!result.containsKey(missedId)) {
            userNameCache.put(KEY_USER_NAME + missedId, NULL_PLACEHOLDER);
          }
        }
      } catch (Exception e) {
        log.warn("[FlowUserCache] 批量获取用户名称失败: {}", e.getMessage());
      }
    }

    return result;
  }

  /**
   * 清除指定用户的缓存。
   *
   * <p>在用户信息变更时调用（监听用户中心变更事件）。
   *
   * @param userId 用户 ID
   */
  public void evictUser(String userId) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    userNameCache.invalidate(KEY_USER_NAME + userId);
    log.debug("[FlowUserCache] evict userId={}", userId);
  }

  /**
   * 清除全部用户缓存。
   *
   * <p>在用户中心数据全量同步时调用。
   */
  public void evictAll() {
    userNameCache.invalidateAll();
    log.info("[FlowUserCache] evictAll");
  }
}
