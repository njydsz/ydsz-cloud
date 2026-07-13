package com.njydsz.pmis.workflow.server.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.domain.enums.definition.FlowNodeType;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowSkipMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程定义元数据缓存服务
 *
 * <p>P1: 使用 Caffeine 本地缓存流程节点和跳转定义，避免每次推进时重复查库。
 * <p>缓存策略：以 definitionId 为 key，缓存该定义下所有节点和 skip 列表，
 *   TTL 30 分钟，流程部署新版本时主动 evict。
 *
 * <p>设计说明：节点和 skip 的全量列表各自仅查库一次（{@code selectByDefinitionId}），
 *   其余按 nodeCode / nextNodeCode / 起始节点 等维度的查询均从缓存列表中派生，
 *   将原本每次推进 5+ 次查库降为首次 2 次、后续 0 次。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
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

    private final Cache<String, List<FlowNodeDO>> nodeCache;
    private final Cache<String, List<FlowSkipDO>> skipCache;

    /**
     * Spring 注入构造器，使用系统时钟。
     */
    public FlowDefinitionCacheService(FlowNodeMapper flowNodeMapper,
                                      FlowSkipMapper flowSkipMapper) {
        this(flowNodeMapper, flowSkipMapper, Ticker.systemTicker());
    }

    /**
     * 测试用构造器，可注入自定义 {@link Ticker} 以模拟 TTL 过期。
     */
    FlowDefinitionCacheService(FlowNodeMapper flowNodeMapper,
                               FlowSkipMapper flowSkipMapper,
                               Ticker ticker) {
        this.flowNodeMapper = flowNodeMapper;
        this.flowSkipMapper = flowSkipMapper;
        this.nodeCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(ticker)
                .build();
        this.skipCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(ticker)
                .build();
    }

    // ============================== 主动失效 ==============================

    /**
     * 清除指定流程定义的全部缓存（节点 + skip）。
     *
     * <p>在流程部署新版本 / 编辑草稿 / 删除定义时调用。
     *
     * @param definitionId 流程定义 ID
     */
    public void evict(String definitionId) {
        if (definitionId == null) {
            return;
        }
        // P0-3: 按租户维度失效缓存（所有租户的同 definitionId 一并清除）
        nodeCache.asMap().keySet().stream()
                .filter(k -> k.endsWith(":" + definitionId))
                .forEach(nodeCache::invalidate);
        skipCache.asMap().keySet().stream()
                .filter(k -> k.endsWith(":" + definitionId))
                .forEach(skipCache::invalidate);
        log.debug("[FlowCache] evict definitionId={}", definitionId);
    }

    // ============================== 节点查询 ==============================

    /**
     * 获取流程定义下全部节点（缓存）。
     */
    public List<FlowNodeDO> getAllNodes(String definitionId) {
        if (definitionId == null) {
            return Collections.emptyList();
        }
        String cacheKey = buildCacheKey(definitionId);
        return nodeCache.get(cacheKey, this::loadNodes);
    }

    /**
     * 按 nodeCode 查单节点。
     */
    public FlowNodeDO getNodeByCode(String definitionId, String nodeCode) {
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
    public FlowNodeDO getStartNode(String definitionId) {
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
    public List<FlowSkipDO> getAllSkips(String definitionId) {
        if (definitionId == null) {
            return Collections.emptyList();
        }
        String cacheKey = buildCacheKey(definitionId);
        return skipCache.get(cacheKey, this::loadSkips);
    }

    /**
     * 查某节点的出发跳转（按 ext.sourceRef == nodeCode 过滤）。
     *
     * <p>返回该节点所有 skipType 的出边，调用方按需过滤 skipType。
     */
    public List<FlowSkipDO> getSkipsByNodeCode(String definitionId, String nodeCode) {
        if (nodeCode == null) {
            return Collections.emptyList();
        }
        return getAllSkips(definitionId).stream()
                .filter(s -> nodeCode.equals(extractSourceRef(s)))
                .collect(Collectors.toList());
    }

    /**
     * 查指向某节点的跳转（按 nextNodeCode 过滤，用于退回时找前驱）。
     */
    public List<FlowSkipDO> getSkipsByNextNode(String definitionId, String nextNodeCode) {
        if (nextNodeCode == null) {
            return Collections.emptyList();
        }
        return getAllSkips(definitionId).stream()
                .filter(s -> nextNodeCode.equals(s.getNextNodeCode()))
                .collect(Collectors.toList());
    }

    // ============================== 内部加载 ==============================

    private List<FlowNodeDO> loadNodes(String cacheKey) {
        // P0-3: cacheKey 格式为 tenantId:definitionId
        String definitionId = extractDefinitionId(cacheKey);
        List<FlowNodeDO> nodes = flowNodeMapper.selectByDefinitionId(definitionId);
        log.debug("[FlowCache] load nodes: definitionId={} count={}",
                definitionId, nodes == null ? 0 : nodes.size());
        return nodes == null ? Collections.emptyList() : nodes;
    }

    private List<FlowSkipDO> loadSkips(String cacheKey) {
        // P0-3: cacheKey 格式为 tenantId:definitionId
        String definitionId = extractDefinitionId(cacheKey);
        List<FlowSkipDO> skips = flowSkipMapper.selectByDefinitionId(definitionId);
        log.debug("[FlowCache] load skips: definitionId={} count={}",
                definitionId, skips == null ? 0 : skips.size());
        return skips == null ? Collections.emptyList() : skips;
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

    private String extractSourceRef(FlowSkipDO skip) {
        if (skip == null || skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            JSONObject extJson = JSON.parseObject(skip.getExt());
            return extJson == null ? null : extJson.getString("sourceRef");
        } catch (Exception e) {
            log.warn("[FlowCache] 解析 skip.ext 失败: skipId={} err={}",
                    skip.getId(), e.getMessage());
            return null;
        }
    }
}
