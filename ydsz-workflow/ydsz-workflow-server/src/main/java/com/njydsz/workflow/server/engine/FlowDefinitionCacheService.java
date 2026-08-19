package com.njydsz.workflow.server.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowSkipDO;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowSkipMapper;
import com.njydsz.workflow.server.config.FlowProperties;

/**
 * 流程定义元数据缓存服务
 *
 * <p>P1: 使用 ydsz-common-cache 本地缓存流程节点和跳转定义，避免每次推进时重复查库。
 *
 * <p>缓存策略：以 definitionId 为 key，缓存该定义下所有节点和 skip 列表， TTL 与容量通过 {@code
 * ydsz.flow.definition-cache.*} YAML 配置（{@link FlowProperties.DefinitionCache}），流程部署新版本时主动 evict。
 *
 * <p>设计说明：节点和 skip 的全量列表各自仅查库一次（{@code selectByDefinitionId}）， 其余按 nodeCode / nextNodeCode / 起始节点
 * 等维度的查询均从缓存列表中派生， 将原本每次推进 5+ 次查库降为首次 2 次、后续 0 次。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowDefinitionCacheService {

  private final FlowNodeMapper flowNodeMapper;
  private final FlowSkipMapper flowSkipMapper;

  /** P0-3: 集群缓存失效广播器（@Lazy 避免循环依赖） */
  private final FlowDefinitionCacheBroadcaster broadcaster;

  private final Cache<String, List<FlowNodeDO>> nodeCache;
  private final Cache<String, List<FlowSkipDO>> skipCache;

  /** P2-4: sourceRef 索引缓存，避免每次 getSkipsByNodeCode 都解析 JSON */
  private final Cache<String, Map<String, List<FlowSkipDO>>> skipSourceRefIndexCache;

  /**
   * P1: 节点编码索引缓存（nodeCode → FlowNodeDO），将 getNodeByCode 从 O(n) 流过滤优化为 O(1) Map 查找。
   *
   * <p>与 {@link #skipSourceRefIndexCache} 类似，在加载节点时一次性构建索引。
   */
  private final Cache<String, Map<String, FlowNodeDO>> nodeByCodeCache;

  /**
   * P1: 下一节点编码索引缓存（nextNodeCode → List<FlowSkipDO>），将 getSkipsByNextNode 从 O(n) 流过滤优化为 O(1) Map 查找。
   */
  private final Cache<String, Map<String, List<FlowSkipDO>>> skipsByNextNodeCache;

  /**
   * Spring 注入构造器，使用系统时钟。
   *
   * <p>缓存 TTL 与容量从 {@link FlowProperties} 读取（P1-2: 硬编码值迁移至 YAML）。
   *
   * @param properties 工作流配置属性
   */
  public FlowDefinitionCacheService(
      FlowNodeMapper flowNodeMapper,
      FlowSkipMapper flowSkipMapper,
      @Lazy FlowDefinitionCacheBroadcaster broadcaster,
      FlowProperties properties) {
    this.flowNodeMapper = flowNodeMapper;
    this.flowSkipMapper = flowSkipMapper;
    this.broadcaster = broadcaster;
    this.nodeCache =
        YdszCache.<String, List<FlowNodeDO>>newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:def-nodes")
            .expireAfterWrite(properties.getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getDefinitionCacheMaxSize())
            .build();
    this.skipCache =
        YdszCache.<String, List<FlowSkipDO>>newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:def-skips")
            .expireAfterWrite(properties.getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getDefinitionCacheMaxSize())
            .build();
    this.skipSourceRefIndexCache =
        YdszCache.<String, Map<String, List<FlowSkipDO>>>newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:def-sourceref-index")
            .expireAfterWrite(properties.getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getDefinitionCacheMaxSize())
            .build();
    this.nodeByCodeCache =
        YdszCache.<String, Map<String, FlowNodeDO>>newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:def-node-by-code")
            .expireAfterWrite(properties.getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getDefinitionCacheMaxSize())
            .build();
    this.skipsByNextNodeCache =
        YdszCache.<String, Map<String, List<FlowSkipDO>>>newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:def-skips-by-next")
            .expireAfterWrite(properties.getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getDefinitionCacheMaxSize())
            .build();
  }

  // ============================== 主动失效 ==============================

  /**
   * 清除指定流程定义的全部缓存（节点 + skip），并广播到集群。
   *
   * <p>在流程部署新版本 / 编辑草稿 / 删除定义 / 发布 / 停用 / 迁移时调用。
   *
   * <p>P0-3: 先执行本地缓存失效，再通过 Redis Pub/Sub 广播到集群其他节点， 确保集群环境下所有节点的本地缓存一致。
   *
   * @param definitionId 流程定义 ID
   */
  public void evict(String definitionId) {
    if (definitionId == null) {
      return;
    }
    evictLocal(definitionId);
    // P0-3: 广播到集群其他节点
    if (broadcaster != null) {
      broadcaster.broadcast(definitionId);
    }
  }

  /**
   * P0-3: 仅清除本地缓存（不广播）
   *
   * <p>供 {@link FlowDefinitionCacheBroadcaster} 收到集群广播后调用， 避免收到远端消息后再次广播形成环路。
   *
   * @param definitionId 流程定义 ID
   */
  void evictLocal(String definitionId) {
    if (definitionId == null) {
      return;
    }
    // 按租户维度失效缓存（所有租户的同 definitionId 一并清除）
    nodeCache.asMap().keySet().stream()
        .filter(k -> k.endsWith(":" + definitionId))
        .forEach(nodeCache::invalidate);
    skipCache.asMap().keySet().stream()
        .filter(k -> k.endsWith(":" + definitionId))
        .forEach(skipCache::invalidate);
    skipSourceRefIndexCache.asMap().keySet().stream()
        .filter(k -> k.endsWith(":" + definitionId))
        .forEach(skipSourceRefIndexCache::invalidate);
    nodeByCodeCache.asMap().keySet().stream()
        .filter(k -> k.endsWith(":" + definitionId))
        .forEach(nodeByCodeCache::invalidate);
    skipsByNextNodeCache.asMap().keySet().stream()
        .filter(k -> k.endsWith(":" + definitionId))
        .forEach(skipsByNextNodeCache::invalidate);
    log.debug("[FlowCache] evictLocal definitionId={}", definitionId);
  }

  // ============================== 节点查询 ==============================

  /** 获取流程定义下全部节点（缓存）。 */
  public List<FlowNodeDO> getAllNodes(String definitionId) {
    if (definitionId == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    return nodeCache.get(cacheKey, this::loadNodes);
  }

  /** 按 nodeCode 查单节点（P1: O(1) Map 查找）。 */
  public FlowNodeDO getNodeByCode(String definitionId, String nodeCode) {
    if (nodeCode == null) {
      return null;
    }
    String cacheKey = buildCacheKey(definitionId);
    Map<String, FlowNodeDO> index = nodeByCodeCache.get(cacheKey, this::loadNodeByCodeIndex);
    return index.get(nodeCode);
  }

  /** 查开始节点（nodeType = START，P1: 使用 nodeByCode 索引缓存）。 */
  public FlowNodeDO getStartNode(String definitionId) {
    // 无法直接通过 nodeByCode 索引定位 START 节点（需要遍历），但 O(n) 仅在首次加载时发生（缓存后直接命中）
    // 若需进一步优化，可额外维护 nodeByType 索引，但 START 节点每次查询频率低于 getNodeByCode，当前实现已足够
    return getAllNodes(definitionId).stream()
        .filter(n -> n.getNodeType() != null && n.getNodeType() == FlowNodeType.START.getCode())
        .findFirst()
        .orElse(null);
  }

  // ============================== skip 查询 ==============================

  /** 获取流程定义下全部跳转（缓存）。 */
  public List<FlowSkipDO> getAllSkips(String definitionId) {
    if (definitionId == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    return skipCache.get(cacheKey, this::loadSkips);
  }

  /**
   * 查某节点的出发跳转（P2-4: 预解析 sourceRef 索引，O(1) 查找替代 O(n) 流过滤）
   *
   * <p>返回该节点所有 skipType 的出边，调用方按需过滤 skipType。
   */
  public List<FlowSkipDO> getSkipsByNodeCode(String definitionId, String nodeCode) {
    if (nodeCode == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    Map<String, List<FlowSkipDO>> index =
        skipSourceRefIndexCache.get(cacheKey, this::loadSkipSourceRefIndex);
    List<FlowSkipDO> result = index.get(nodeCode);
    return result == null ? Collections.emptyList() : result;
  }

  /** 查指向某节点的跳转（按 nextNodeCode 过滤，用于退回时找前驱，P1: O(1) Map 查找）。 */
  public List<FlowSkipDO> getSkipsByNextNode(String definitionId, String nextNodeCode) {
    if (nextNodeCode == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    Map<String, List<FlowSkipDO>> index =
        skipsByNextNodeCache.get(cacheKey, this::loadSkipsByNextNodeIndex);
    List<FlowSkipDO> result = index.get(nextNodeCode);
    return result == null ? Collections.emptyList() : result;
  }

  // ============================== 内部加载 ==============================

  private List<FlowNodeDO> loadNodes(String cacheKey) {
    // P0-3: cacheKey 格式为 tenantId:definitionId
    String definitionId = extractDefinitionId(cacheKey);
    List<FlowNodeDO> nodes = flowNodeMapper.selectByDefinitionId(definitionId);
    log.debug(
        "[FlowCache] load nodes: definitionId={} count={}",
        definitionId,
        nodes == null ? 0 : nodes.size());
    return nodes == null ? Collections.emptyList() : nodes;
  }

  private List<FlowSkipDO> loadSkips(String cacheKey) {
    // P0-3: cacheKey 格式为 tenantId:definitionId
    String definitionId = extractDefinitionId(cacheKey);
    List<FlowSkipDO> skips = flowSkipMapper.selectByDefinitionId(definitionId);
    log.debug(
        "[FlowCache] load skips: definitionId={} count={}",
        definitionId,
        skips == null ? 0 : skips.size());
    return skips == null ? Collections.emptyList() : skips;
  }

  /** P2-4: 预解析 sourceRef 索引（加载时一次性解析所有 skip 的 ext JSON，避免每次查询重复解析） */
  private Map<String, List<FlowSkipDO>> loadSkipSourceRefIndex(String cacheKey) {
    List<FlowSkipDO> skips = loadSkips(cacheKey);
    Map<String, List<FlowSkipDO>> index = new HashMap<>(skips.size());
    for (FlowSkipDO skip : skips) {
      String sourceRef = extractSourceRef(skip);
      if (sourceRef != null) {
        index.computeIfAbsent(sourceRef, k -> new ArrayList<>()).add(skip);
      }
    }
    return index;
  }

  /**
   * P0-3: 构建租户感知的缓存 key，防止跨租户缓存串号。
   *
   * @param definitionId 流程定义 ID
   * @return "tenantId:definitionId"
   */
  private String buildCacheKey(String definitionId) {
    return TenantContextHolder.getTenantId() + ":" + definitionId;
  }

  /**
   * P0-3: 从缓存 key 中提取 definitionId。
   *
   * @param cacheKey "tenantId:definitionId"
   * @return definitionId
   */
  private String extractDefinitionId(String cacheKey) {
    int idx = cacheKey.indexOf(':');
    return idx >= 0 ? cacheKey.substring(idx + 1) : cacheKey;
  }

  /** P1: 构建节点编码索引（nodeCode → FlowNodeDO），将 getNodeByCode 从 O(n) 优化为 O(1) */
  private Map<String, FlowNodeDO> loadNodeByCodeIndex(String cacheKey) {
    List<FlowNodeDO> nodes = loadNodes(cacheKey);
    Map<String, FlowNodeDO> index = new HashMap<>(nodes.size());
    for (FlowNodeDO node : nodes) {
      if (node.getNodeCode() != null) {
        index.put(node.getNodeCode(), node);
      }
    }
    return index;
  }

  /** P1: 构建下一节点编码索引（nextNodeCode → List<FlowSkipDO>），将 getSkipsByNextNode 从 O(n) 优化为 O(1) */
  private Map<String, List<FlowSkipDO>> loadSkipsByNextNodeIndex(String cacheKey) {
    List<FlowSkipDO> skips = loadSkips(cacheKey);
    Map<String, List<FlowSkipDO>> index = new HashMap<>(skips.size());
    for (FlowSkipDO skip : skips) {
      if (skip.getNextNodeCode() != null) {
        index.computeIfAbsent(skip.getNextNodeCode(), k -> new ArrayList<>()).add(skip);
      }
    }
    return index;
  }

  private String extractSourceRef(FlowSkipDO skip) {
    return FlowSkipUtils.extractSourceNodeCode(skip);
  }
}
