package com.njydsz.cronjob.server.core.discovery;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.core.redis.CronjobRedisOps;

/**
 * P1-1: 节点黑名单（Redis -backed）。
 *
 * <p>存储被自动隔离或手动拉黑的节点 ID 集合，供节点选择器过滤。使用 Redis 键值实现，
 * 支持跨 Leader 实例共享黑名单状态。
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>节点连续失败次数超阈值 → 自动加入黑名单
 *   <li>运维手动拉黑节点（通过 API）
 *   <li>节点响应时长持续过高 → 自动隔离
 * </ul>
 *
 * <h3>键设计</h3>
 *
 * <p>Redis Key: {@code ydsz:job:node:blacklist:{nodeId}}（String 类型，带 TTL）
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeBlacklist {

  /** Redis key segment 前缀 */
  private static final String BLACKLIST_SEGMENT_PREFIX = "node:blacklist:";

  /** 默认黑名单 TTL：1 小时（防止永久隔离） */
  private static final long DEFAULT_TTL_SECONDS = 3600L;

  private final CronjobRedisOps cronjobRedisOps;

  /**
   * 将节点加入黑名单。
   *
   * @param nodeId 节点 ID
   * @param ttl TTL（过期后自动移除）
   */
  public void add(String nodeId, Duration ttl) {
    if (nodeId == null || nodeId.isBlank()) {
      return;
    }
    try {
      String segment = BLACKLIST_SEGMENT_PREFIX + nodeId;
      long ttlSeconds = ttl != null ? ttl.getSeconds() : DEFAULT_TTL_SECONDS;
      cronjobRedisOps.set(segment, "1", ttlSeconds);
      log.info("[NodeBlacklist] 节点已加入黑名单: nodeId={} ttl={}s", nodeId, ttlSeconds);
    } catch (Exception e) {
      log.warn("[NodeBlacklist] 加入黑名单失败: nodeId={} reason={}", nodeId, e.getMessage());
    }
  }

  /**
   * 将节点加入黑名单（使用默认 TTL）。
   *
   * @param nodeId 节点 ID
   */
  public void add(String nodeId) {
    add(nodeId, Duration.ofSeconds(DEFAULT_TTL_SECONDS));
  }

  /**
   * 将节点从黑名单移除。
   *
   * @param nodeId 节点 ID
   */
  public void remove(String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      return;
    }
    try {
      String segment = BLACKLIST_SEGMENT_PREFIX + nodeId;
      cronjobRedisOps.delete(segment);
      log.info("[NodeBlacklist] 节点已从黑名单移除: nodeId={}", nodeId);
    } catch (Exception e) {
      log.warn("[NodeBlacklist] 移除黑名单失败: nodeId={} reason={}", nodeId, e.getMessage());
    }
  }

  /**
   * 检查节点是否在黑名单中。
   *
   * @param nodeId 节点 ID
   * @return true 在黑名单中；false 不在或查询异常
   */
  public boolean contains(String nodeId) {
    if (nodeId == null || nodeId.isBlank()) {
      return false;
    }
    try {
      String segment = BLACKLIST_SEGMENT_PREFIX + nodeId;
      return cronjobRedisOps.exists(segment);
    } catch (Exception e) {
      log.warn("[NodeBlacklist] 查询黑名单异常: nodeId={} reason={}", nodeId, e.getMessage());
      return false;
    }
  }

  /**
   * 获取所有黑名单节点 ID。
   *
   * @return 节点 ID 集合（当前返回空集合，如需全量查询可使用 Redis SCAN）
   */
  public Set<String> getAll() {
    // 简化实现：实际场景可使用 Redis SCAN 遍历 ydsz:job:node:blacklist:* 前缀
    return Collections.emptySet();
  }

  /**
   * 批量过滤：移除在黑名单中的节点。
   *
   * @param nodeIds 候选节点 ID 集合
   * @return 过滤后的节点 ID 集合
   */
  public Set<String> filterBlacklisted(Set<String> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return Collections.emptySet();
    }
    return nodeIds.stream().filter(id -> !contains(id)).collect(Collectors.toSet());
  }
}
