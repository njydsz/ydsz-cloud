package com.njydsz.workflow.server.engine.FlowDefinitionCacheService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowSkipRepository;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.server.config.FlowProperties;

/**
 * 流程定义元数据缓存服务
 *
 * <p>P1: 使用 ydsz-common-cache 本地缓存流程节点和跳转定义，避免每次推进时重复查库。
 *
 * <p>缓存策略：以 definitionId 为 key，缓存该定义下所有节点和 skip 列表， TTL 与容量通过 {@code
 * ydsz.flow.definition-cache.*} YAML 配置（{@link FlowProperties.DefinitionCache}），流程部署新版本时主动 evict。
 *
 * <p>设计说明：节点和 skip 的全量列表各自仅查库一次（{@code findByDefinitionId}）， 其余按 nodeCode / nextNodeCode / 起始节点
 * 等维度的查询均从缓存列表中派生， 将原本每次推进 5+ 次查库降为首次 2 次、后续 0 次。
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper（符合 §34.2.3）。Repository 返回领域 VO，无需 DO → VO 转换。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowDefinitionCacheService {


  private final FlowNodeRepository flowNodeRepository;
  private final FlowSkipRepository flowSkipRepository;

  /**
   * P0-3: 集群缓存失效广播器（ObjectProvider 处理可选 bean，当 crossInstanceEnabled=false 时 bean 不存在）
   */
  private final ObjectProvider<FlowDefinitionCacheBroadcaster> broadcasterProvider;

  private final Cache<String, FlowDefinitionMetadata> metadataCache;

  /**
   * Spring 注入构造器，使用系统时钟。
   *
   * <p>缓存 TTL 与容量从 {@link FlowProperties} 读取（P1-2: 硬编码值迁移至 YAML）。
   *
   * @param flowNodeRepository 流程节点仓储接口
   * @param flowSkipRepository 跳转规则仓储接口
   * @param broadcasterProvider 集群缓存失效广播器提供者（可选）
   * @param properties 工作流配置属性
   */
  public FlowDefinitionCacheService(
      FlowNodeRepository flowNodeRepository,
      FlowSkipRepository flowSkipRepository,
      ObjectProvider<FlowDefinitionCacheBroadcaster> broadcasterProvider,
      FlowProperties properties) {
    this.flowNodeRepository = flowNodeRepository;
    this.flowSkipRepository = flowSkipRepository;
    this.broadcasterProvider = broadcasterProvider;
    this.metadataCache =
        YdszCache.<String, FlowDefinitionMetadata>newBuilder()
            .type(CacheType.STRIPED)
            .name("flow:def-metadata")
            .expireAfterWrite(properties.getDefinitionCache().getDefinitionCacheTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getDefinitionCache().getDefinitionCacheMaxSize())
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
    // P0-3: 广播到集群其他节点（仅当 crossInstanceEnabled=true 时 broadcaster bean 存在）
    FlowDefinitionCacheBroadcaster broadcaster = broadcasterProvider.getIfAvailable();
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
    metadataCache.asMap().keySet().stream()
        .filter(k -> k.endsWith(":" + definitionId))
        .forEach(metadataCache::invalidate);
    log.debug("[FlowCache] evictLocal definitionId={}", definitionId);
  }

  // ============================== 节点查询 ==============================

  /**
   * 获取流程定义下全部节点（缓存）。
   *
   * @param definitionId 流程定义 ID
   * @return 全部节点列表；无数据返回空列表
   */
  public List<FlowNodeVO> getAllNodes(String definitionId) {
    if (definitionId == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    FlowDefinitionMetadata metadata = metadataCache.get(cacheKey, this::loadMetadata);
    return metadata != null ? metadata.getNodes() : Collections.emptyList();
  }

  /**
   * 按 nodeCode 查单节点（P1: O(1) Map 查找）。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return 匹配的节点实体；不存在返回 null
   */
  public FlowNodeVO getNodeByCode(String definitionId, String nodeCode) {
    if (nodeCode == null) {
      return null;
    }
    String cacheKey = buildCacheKey(definitionId);
    FlowDefinitionMetadata metadata = metadataCache.get(cacheKey, this::loadMetadata);
    return metadata != null ? metadata.getNodeByCode().get(nodeCode) : null;
  }

  /**
   * 查开始节点（nodeType = START，P1: 使用 nodeByCode 索引缓存）。
   *
   * @param definitionId 流程定义 ID
   * @return 开始节点实体；不存在返回 null
   */
  public FlowNodeVO getStartNode(String definitionId) {
    return metadataCache.get(buildCacheKey(definitionId), this::loadMetadata).getNodes().stream()
        .filter(n -> n.getNodeType() != null && n.getNodeType() == FlowNodeType.START.getCode())
        .findFirst()
        .orElse(null);
  }

  // ============================== skip 查询 ==============================

  /**
   * 获取流程定义下全部跳转（缓存）。
   *
   * @param definitionId 流程定义 ID
   * @return 全部跳转列表；无数据返回空列表
   */
  public List<FlowSkipVO> getAllSkips(String definitionId) {
    if (definitionId == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    FlowDefinitionMetadata metadata = metadataCache.get(cacheKey, this::loadMetadata);
    return metadata != null ? metadata.getSkips() : Collections.emptyList();
  }

  /**
   * 查某节点的出发跳转（P2-4: 预解析 sourceRef 索引，O(1) 查找替代 O(n) 流过滤）
   *
   * <p>返回该节点所有 skipType 的出边，调用方按需过滤 skipType。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 源节点编码
   * @return 该节点的出发跳转列表；无数据返回空列表
   */
  public List<FlowSkipVO> getSkipsByNodeCode(String definitionId, String nodeCode) {
    if (nodeCode == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    FlowDefinitionMetadata metadata = metadataCache.get(cacheKey, this::loadMetadata);
    if (metadata == null) {
      return Collections.emptyList();
    }
    List<FlowSkipVO> result = metadata.getSkipsBySource().get(nodeCode);
    return result == null ? Collections.emptyList() : result;
  }

  /**
   * 查指向某节点的跳转（按 nextNodeCode 过滤，用于退回时找前驱，P1: O(1) Map 查找）。
   *
   * @param definitionId 流程定义 ID
   * @param nextNodeCode 目标节点编码
   * @return 指向该节点的跳转列表；无数据返回空列表
   */
  public List<FlowSkipVO> getSkipsByNextNode(String definitionId, String nextNodeCode) {
    if (nextNodeCode == null) {
      return Collections.emptyList();
    }
    String cacheKey = buildCacheKey(definitionId);
    FlowDefinitionMetadata metadata = metadataCache.get(cacheKey, this::loadMetadata);
    if (metadata == null) {
      return Collections.emptyList();
    }
    List<FlowSkipVO> result = metadata.getSkipsByTarget().get(nextNodeCode);
    return result == null ? Collections.emptyList() : result;
  }

  // ============================== 内部加载 ==============================

  private List<FlowNodeVO> loadNodes(String cacheKey) {
    // P0-3: cacheKey 格式为 tenantId:definitionId
    String definitionId = extractDefinitionId(cacheKey);
    List<FlowNodeVO> nodes = flowNodeRepository.findByDefinitionId(definitionId);
    log.debug(
        "[FlowCache] load nodes: definitionId={} count={}",
        definitionId,
        nodes.size());
    return nodes;
  }

  private List<FlowSkipVO> loadSkips(String cacheKey) {
    // P0-3: cacheKey 格式为 tenantId:definitionId
    String definitionId = extractDefinitionId(cacheKey);
    List<FlowSkipVO> skips = flowSkipRepository.findByDefinitionId(definitionId);
    log.debug(
        "[FlowCache] load skips: definitionId={} count={}",
        definitionId,
        skips.size());
    return skips;
  }

  /**
   * 一次性加载完整元数据并构建索引。
   *
   * <p>替代原有的多个独立缓存加载方法（loadNodes/loadSkips/loadSkipSourceRefIndex/
   * loadNodeByCodeIndex/loadSkipsByNextNodeIndex）， 单次缓存调用完成全部数据加载和索引构建，减少缓存操作次数。
   *
   * @param cacheKey 缓存 key（tenantId:definitionId）
   * @return 完整元数据对象（含节点、跳转及索引）
   */
  private FlowDefinitionMetadata loadMetadata(String cacheKey) {
    List<FlowNodeVO> nodes = loadNodes(cacheKey);
    List<FlowSkipVO> skips = loadSkips(cacheKey);

    Map<String, FlowNodeVO> nodeByCode = new HashMap<>(nodes.size());
    for (FlowNodeVO node : nodes) {
      if (node.getNodeCode() != null) {
        nodeByCode.put(node.getNodeCode(), node);
      }
    }

    Map<String, List<FlowSkipVO>> skipsBySource = new HashMap<>(skips.size());
    for (FlowSkipVO skip : skips) {
      String sourceRef = extractSourceRef(skip);
      if (sourceRef != null) {
        skipsBySource.computeIfAbsent(sourceRef, k -> new ArrayList<>(8))