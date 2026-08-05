package com.remisoft.workflow.server.engine;

import com.remisoft.common.util.collection.MapUtils;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.remisoft.common.json.RemiJson;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.remisoft.common.cache.RemiCache;
import com.remisoft.common.cache.api.Cache;
import com.remisoft.common.cache.builder.CacheType;
import com.remisoft.common.security.TenantContext;
import com.remisoft.workflow.domain.entity.FlowNode;
import com.remisoft.workflow.domain.entity.FlowSkip;
import com.remisoft.workflow.domain.enums.FlowNodeType;
import com.remisoft.workflow.infra.mapper.FlowNodeMapper;
import com.remisoft.workflow.infra.mapper.FlowSkipMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
/**
 * 流程定义元数据缓存服务
 *
 * <p>P1: 使用 remi-common-cache 本地缓存流程节点和跳转定义，避免每次推进时重复查库。
 * <p>缓存策略：以 definitionId 为 key，缓存该定义下所有节点和 skip 列表，
 *   TTL 30 分钟，流程部署新版本时主动 evict。
 *
 * <p>设计说明：节点和 skip 的全量列表各自仅查库一次（{@code selectByDefinitionId}），
 *   其余按 nodeCode / nextNodeCode / 起始节点 等维度的查询均从缓存列表中派生，
 *   将原本每次推进 5+ 次查库降为首次 2 次、后续 0 次。
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
@Component
public class FlowDefinitionCacheService {

    /** 缓存 TTL：30 分钟自动过期 */
    private static final Duration TTL = Duration.ofMinutes(30);
    /** 最大缓存流程定义数 */
    private static final int MAX_SIZE = 1000;

    private final FlowNodeMapper flowNodeMapper;
    private final FlowSkipMapper flowSkipMapper;
    /** P0-3: 集群缓存失效广播器（@Lazy 避免循环依赖） */
    private final FlowDefinitionCacheBroadcaster broadcaster;

    private final Cache<String, List<FlowNode>> nodeCache;
    private final Cache<String, List<FlowSkip>> skipCache;
    /** P2-4: sourceRef 索引缓存，避免每次 getSkipsByNodeCode 都解析 JSON */
    private final Cache<String, Map<String, List<FlowSkip>>> skipSourceRefIndexCache;

    /**
     * Spring 注入构造器，使用系统时钟。
     */
    public FlowDefinitionCacheService(FlowNodeMapper flowNodeMapper,
                                      FlowSkipMapper flowSkipMapper,
                                      @Lazy FlowDefinitionCacheBroadcaster broadcaster) {
        this(flowNodeMapper, flowSkipMapper, broadcaster, null);
    }

    /**
     * 测试用构造器。
     */
    FlowDefinitionCacheService(FlowNodeMapper flowNodeMapper,
                               FlowSkipMapper flowSkipMapper,
                               FlowDefinitionCacheBroadcaster broadcaster,
                               Object unused) {
        this.flowNodeMapper = flowNodeMapper;
        this.flowSkipMapper = flowSkipMapper;
        this.broadcaster = broadcaster;
        this.nodeCache = RemiCache.<String, List<FlowNode>>newBuilder()
                .type(CacheType.STRIPED)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(MAX_SIZE)
                .build();
        this.skipCache = RemiCache.<String, List<FlowSkip>>newBuilder()
                .type(CacheType.STRIPED)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(MAX_SIZE)
                .build();
        this.skipSourceRefIndexCache = RemiCache.<String, Map<String, List<FlowSkip>>>newBuilder()
                .type(CacheType.STRIPED)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(MAX_SIZE)
                .build();
    }

    // ============================== 主动失效 ==============================

    /**
     * 清除指定流程定义的全部缓存（节点 + skip），并广播到集群。
     *
     * <p>在流程部署新版本 / 编辑草稿 / 删除定义 / 发布 / 停用 / 迁移时调用。
     *
     * <p>P0-3: 先执行本地缓存失效，再通过 Redis Pub/Sub 广播到集群其他节点，
     * 确保集群环境下所有节点的本地缓存一致。
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
     * <p>供 {@link FlowDefinitionCacheBroadcaster} 收到集群广播后调用，
     * 避免收到远端消息后再次广播形成环路。
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
        log.debug("[FlowCache] evictLocal definitionId={}", definitionId);
    }

    // ============================== 节点查询 ==============================

    /**
     * 获取流程定义下全部节点（缓存）。
     */
    public List<FlowNode> getAllNodes(String definitionId) {
        if (definitionId == null) {
            return Collections.emptyList();
        }
        String cacheKey = buildCacheKey(definitionId);
        return nodeCache.get(cacheKey, this::loadNodes);
    }

    /**
     * 按 nodeCode 查单节点。
     */
    public FlowNode getNodeByCode(String definitionId, String nodeCode) {
        if (nodeCode == null) {
            return null;
        }
        return getAllNodes(definitionId).stream()
                .filter(n -> nodeCode.equals(n.getNodeCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查开始节点（nodeType = START）。
     */
    public FlowNode getStartNode(String definitionId) {
        return getAllNodes(definitionId).stream()
                .filter(n -> n.getNodeType() != null
                        && n.getNodeType() == FlowNodeType.START.getCode())
                .findFirst()
                .orElse(null);
    }

    // ============================== skip 查询 ==============================

    /**
     * 获取流程定义下全部跳转（缓存）。
     */
    public List<FlowSkip> getAllSkips(String definitionId) {
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
    public List<FlowSkip> getSkipsByNodeCode(String definitionId, String nodeCode) {
        if (nodeCode == null) {
            return Collections.emptyList();
        }
        String cacheKey = buildCacheKey(definitionId);
        Map<String, List<FlowSkip>> index = skipSourceRefIndexCache.get(cacheKey, this::loadSkipSourceRefIndex);
        List<FlowSkip> result = index.get(nodeCode);
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * 查指向某节点的跳转（按 nextNodeCode 过滤，用于退回时找前驱）。
     */
    public List<FlowSkip> getSkipsByNextNode(String definitionId, String nextNodeCode) {
        if (nextNodeCode == null) {
            return Collections.emptyList();
        }
        return getAllSkips(definitionId).stream()
                .filter(s -> nextNodeCode.equals(s.getNextNodeCode()))
                .collect(Collectors.toList());
    }

    // ============================== 内部加载 ==============================

    private List<FlowNode> loadNodes(String cacheKey) {
        // P0-3: cacheKey 格式为 tenantId:definitionId
        String definitionId = extractDefinitionId(cacheKey);
        List<FlowNode> nodes = flowNodeMapper.selectByDefinitionId(definitionId);
        log.debug("[FlowCache] load nodes: definitionId={} count={}",
                definitionId, nodes == null ? 0 : nodes.size());
        return nodes == null ? Collections.emptyList() : nodes;
    }

    private List<FlowSkip> loadSkips(String cacheKey) {
        // P0-3: cacheKey 格式为 tenantId:definitionId
        String definitionId = extractDefinitionId(cacheKey);
        List<FlowSkip> skips = flowSkipMapper.selectByDefinitionId(definitionId);
        log.debug("[FlowCache] load skips: definitionId={} count={}",
                definitionId, skips == null ? 0 : skips.size());
        return skips == null ? Collections.emptyList() : skips;
    }

    /**
     * P2-4: 预解析 sourceRef 索引（加载时一次性解析所有 skip 的 ext JSON，避免每次查询重复解析）
     */
    private Map<String, List<FlowSkip>> loadSkipSourceRefIndex(String cacheKey) {
        List<FlowSkip> skips = loadSkips(cacheKey);
        Map<String, List<FlowSkip>> index = new HashMap<>(skips.size());
        for (FlowSkip skip : skips) {
            String sourceRef = extractSourceRef(skip);
            if (sourceRef != null) {
                index.computeIfAbsent(sourceRef, k -> new ArrayList<>()).add(skip);
            }
        }
        return index;
    }

    /**
     * 从 skip 的 ext JSON 中提取 sourceRef（出发节点编码）。
     *
     * <p>skip 表无 source_node_code 列，源节点编码冗余存储在 ext JSON 的 sourceRef 字段
     * （见 FlowDefinitionServiceImpl 部署逻辑）。
     */
    /**
     * P0-3: 构建租户感知的缓存 key，防止跨租户缓存串号。
     *
     * @param definitionId 流程定义 ID
     * @return "tenantId:definitionId"
     */
    private String buildCacheKey(String definitionId) {
        return TenantContext.getTenantId() + ":" + definitionId;
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

    private String extractSourceRef(FlowSkip skip) {
        if (skip == null || skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> extJson = RemiJson.parseMap(skip.getExt());
            return extJson == null ? null : MapUtils.getString(extJson, "sourceRef");
        } catch (Exception e) {
            log.warn("[FlowCache] 解析 skip.ext 失败: skipId={} err={}",
                    skip.getId(), e.getMessage());
            return null;
        }
    }
}
