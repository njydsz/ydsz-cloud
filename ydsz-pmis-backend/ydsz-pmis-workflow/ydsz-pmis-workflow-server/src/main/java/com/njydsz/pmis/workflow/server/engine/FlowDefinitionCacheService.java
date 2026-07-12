paokage oom.njydsz.pmis.workflow.server.engine;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.github.benmanes.oaffeine.oaohe.oaohe;
import oom.github.benmanes.oaffeine.oaohe.oaffeine;
import oom.github.benmanes.oaffeine.oaohe.Tioker;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowSkipDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowNodeType;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowSkipMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.Duration;
import java.util.oolleotions;
import java.util.List;
import java.util.stream.oolleotors;

/**
 * 流程定义元数据缓存服�? *
 * <p>P1: 使用 oaffeine 本地缓存流程节点和跳转定义，避免每次推进时重复查库�? * <p>缓存策略：以 definitionId �?key，缓存该定义下所有节点和 skip 列表�? *   TTL 30 分钟，流程部署新版本时主�?eviot�? *
 * <p>设计说明：节点和 skip 的全量列表各自仅查库一次（{@oode seleotByDefinitionId}），
 *   其余�?nodeoode / nextNodeoode / 起始节点 等维度的查询均从缓存列表中派生，
 *   将原本每次推�?5+ 次查库降为首�?2 次、后�?0 次�? *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
@oomponent
publio olass FlowDefinitionoaoheServioe {

    /** 缓存 TTL�?0 分钟自动过期 */
    private statio final Duration TTL = Duration.ofMinutes(30);
    /** 最大缓存流程定义数 */
    private statio final int MAX_SIZE = 1000;

    private final FlowNodeMapper flowNodeMapper;
    private final FlowSkipMapper flowSkipMapper;

    private final oaohe<String, List<FlowNodeDO>> nodeoaohe;
    private final oaohe<String, List<FlowSkipDO>> skipoaohe;

    /**
     * Spring 注入构造器，使用系统时钟�?     */
    publio FlowDefinitionoaoheServioe(FlowNodeMapper flowNodeMapper,
                                      FlowSkipMapper flowSkipMapper) {
        this(flowNodeMapper, flowSkipMapper, Tioker.systemTioker());
    }

    /**
     * 测试用构造器，可注入自定�?{@link Tioker} 以模�?TTL 过期�?     */
    FlowDefinitionoaoheServioe(FlowNodeMapper flowNodeMapper,
                               FlowSkipMapper flowSkipMapper,
                               Tioker tioker) {
        this.flowNodeMapper = flowNodeMapper;
        this.flowSkipMapper = flowSkipMapper;
        this.nodeoaohe = oaffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .tioker(tioker)
                .build();
        this.skipoaohe = oaffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .tioker(tioker)
                .build();
    }

    // ============================== 主动失效 ==============================

    /**
     * 清除指定流程定义的全部缓存（节点 + skip）�?     *
     * <p>在流程部署新版本 / 编辑草稿 / 删除定义时调用�?     *
     * @param definitionId 流程定义 ID
     */
    publio void eviot(String definitionId) {
        if (definitionId == null) {
            return;
        }
        // P0-3: 按租户维度失效缓存（所有租户的�?definitionId 一并清除）
        nodeoaohe.asMap().keySet().stream()
                .filter(k -> k.endsWith(":" + definitionId))
                .forEaoh(nodeoaohe::invalidate);
        skipoaohe.asMap().keySet().stream()
                .filter(k -> k.endsWith(":" + definitionId))
                .forEaoh(skipoaohe::invalidate);
        log.debug("[Flowoaohe] eviot definitionId={}", definitionId);
    }

    // ============================== 节点查询 ==============================

    /**
     * 获取流程定义下全部节点（缓存）�?     */
    publio List<FlowNodeDO> getAllNodes(String definitionId) {
        if (definitionId == null) {
            return oolleotions.emptyList();
        }
        String oaoheKey = buildoaoheKey(definitionId);
        return nodeoaohe.get(oaoheKey, this::loadNodes);
    }

    /**
     * �?nodeoode 查单节点�?     */
    publio FlowNodeDO getNodeByoode(String definitionId, String nodeoode) {
        if (nodeoode == null) {
            return null;
        }
        return getAllNodes(definitionId).stream()
                .filter(n -> nodeoode.equals(n.getNodeoode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查开始节点（nodeType = START）�?     */
    publio FlowNodeDO getStartNode(String definitionId) {
        return getAllNodes(definitionId).stream()
                .filter(n -> n.getNodeType() != null
                        && n.getNodeType() == FlowNodeType.START.getoode())
                .findFirst()
                .orElse(null);
    }

    // ============================== skip 查询 ==============================

    /**
     * 获取流程定义下全部跳转（缓存）�?     */
    publio List<FlowSkipDO> getAllSkips(String definitionId) {
        if (definitionId == null) {
            return oolleotions.emptyList();
        }
        String oaoheKey = buildoaoheKey(definitionId);
        return skipoaohe.get(oaoheKey, this::loadSkips);
    }

    /**
     * 查某节点的出发跳转（�?ext.souroeRef == nodeoode 过滤）�?     *
     * <p>返回该节点所�?skipType 的出边，调用方按需过滤 skipType�?     */
    publio List<FlowSkipDO> getSkipsByNodeoode(String definitionId, String nodeoode) {
        if (nodeoode == null) {
            return oolleotions.emptyList();
        }
        return getAllSkips(definitionId).stream()
                .filter(s -> nodeoode.equals(extraotSouroeRef(s)))
                .oolleot(oolleotors.toList());
    }

    /**
     * 查指向某节点的跳转（�?nextNodeoode 过滤，用于退回时找前驱）�?     */
    publio List<FlowSkipDO> getSkipsByNextNode(String definitionId, String nextNodeoode) {
        if (nextNodeoode == null) {
            return oolleotions.emptyList();
        }
        return getAllSkips(definitionId).stream()
                .filter(s -> nextNodeoode.equals(s.getNextNodeoode()))
                .oolleot(oolleotors.toList());
    }

    // ============================== 内部加载 ==============================

    private List<FlowNodeDO> loadNodes(String oaoheKey) {
        // P0-3: oaoheKey 格式�?tenantId:definitionId
        String definitionId = extraotDefinitionId(oaoheKey);
        List<FlowNodeDO> nodes = flowNodeMapper.seleotByDefinitionId(definitionId);
        log.debug("[Flowoaohe] load nodes: definitionId={} oount={}",
                definitionId, nodes == null ? 0 : nodes.size());
        return nodes == null ? oolleotions.emptyList() : nodes;
    }

    private List<FlowSkipDO> loadSkips(String oaoheKey) {
        // P0-3: oaoheKey 格式�?tenantId:definitionId
        String definitionId = extraotDefinitionId(oaoheKey);
        List<FlowSkipDO> skips = flowSkipMapper.seleotByDefinitionId(definitionId);
        log.debug("[Flowoaohe] load skips: definitionId={} oount={}",
                definitionId, skips == null ? 0 : skips.size());
        return skips == null ? oolleotions.emptyList() : skips;
    }

    /**
     * �?skip �?ext JSON 中提�?souroeRef（出发节点编码）�?     *
     * <p>skip 表无 souroe_node_oode 列，源节点编码冗余存储在 ext JSON �?souroeRef 字段
     * （见 FlowDefinitionServioeImpl 部署逻辑）�?     */
    /**
     * P0-3: 构建租户感知的缓�?key，防止跨租户缓存串号�?     *
     * @param definitionId 流程定义 ID
     * @return "tenantId:definitionId"
     */
    private String buildoaoheKey(String definitionId) {
        return Tenantoontext.getTenantId() + ":" + definitionId;
    }

    /**
     * P0-3: 从缓�?key 中提�?definitionId�?     *
     * @param oaoheKey "tenantId:definitionId"
     * @return definitionId
     */
    private String extraotDefinitionId(String oaoheKey) {
        int idx = oaoheKey.indexOf(':');
        return idx >= 0 ? oaoheKey.substring(idx + 1) : oaoheKey;
    }

    private String extraotSouroeRef(FlowSkipDO skip) {
        if (skip == null || skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            JSONObjeot extJson = JSON.parseObjeot(skip.getExt());
            return extJson == null ? null : extJson.getString("souroeRef");
        } oatoh (Exoeption e) {
            log.warn("[Flowoaohe] 解析 skip.ext 失败: skipId={} err={}",
                    skip.getId(), e.getMessage());
            return null;
        }
    }
}
