package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.constant.CacheConstants;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.engine.BpmnModel;
import com.njydsz.pmis.workflow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.engine.FlowGraphValidator;
import com.njydsz.pmis.workflow.engine.JsonHelper;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.enums.FlowSkipType;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 流程定义 Service 实现
 *
 * <p>支持 BPMN 2.0 XML 与轻量 JSON 两种部署模式。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

    private final FlowDefinitionMapper definitionMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowSkipMapper skipMapper;
    private final BpmnXmlParser bpmnXmlParser;
    private final FlowGraphValidator graphValidator;
    /** P1: 流程定义元数据缓存，部署/更新时主动失效 */
    private final FlowDefinitionCacheService flowDefinitionCacheService;
    /**
     * GAP-P1-6: 自注入代理引用，使 {@link #batchDeployFromZip} 内部调用 {@link #deploy}
     * 时能正确触发 Spring 事务代理（避免 self-invocation 导致事务失效）。
     * 使用 {@code @Lazy} 打破启动期循环依赖。
     */
    private final FlowDefinitionServiceImpl self;
    /** P2-5: 流程实例 Mapper，用于变更影响分析 */
    private final FlowInstanceMapper instanceMapper;

    /**
     * P2-4: 设计器协同编辑锁定超时阈值（分钟）。
     *
     * <p>超过此时间未续约的锁视为已过期，可被其他用户抢占。
     * 默认 30 分钟，对标钉钉/飞书设计器协同编辑的默认锁定时长。
     */
    @Value("${workflow.designer.lock-timeout-minutes:30}")
    private long lockTimeoutMinutes;

    public FlowDefinitionServiceImpl(
            FlowDefinitionMapper definitionMapper,
            FlowNodeMapper nodeMapper,
            FlowSkipMapper skipMapper,
            BpmnXmlParser bpmnXmlParser,
            FlowGraphValidator graphValidator,
            FlowDefinitionCacheService flowDefinitionCacheService,
            FlowInstanceMapper instanceMapper,
            @Lazy FlowDefinitionServiceImpl self) {
        this.definitionMapper = definitionMapper;
        this.nodeMapper = nodeMapper;
        this.skipMapper = skipMapper;
        this.bpmnXmlParser = bpmnXmlParser;
        this.graphValidator = graphValidator;
        this.flowDefinitionCacheService = flowDefinitionCacheService;
        this.instanceMapper = instanceMapper;
        this.self = self;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public String deploy(FlowDeployProcessDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFlowCode())
                || !StringUtils.hasText(dto.getFlowName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "flowCode/flowName 不能为空");
        }

        String version = StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0";
        // P2-16: 多租户上下文 - DTO 显式传入优先，否则从 SecurityContext 获取，最后兜底 1L
        String tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : SecurityContext.getTenantIdOrDefault("1");

        // 1. 检查重名：同 flowCode + version + tenant 只能有一条
        FlowDefinitionDO existing = definitionMapper.selectPublished(
                dto.getFlowCode(), version, tenantId);
        if (existing != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "流程定义已存在: code=" + dto.getFlowCode() + " version=" + version);
        }

        // 2. 解析 BPMN / JSON 模型
        boolean hasBpmn = StringUtils.hasText(dto.getBpmnXml());
        boolean hasJson = dto.getNodes() != null && !dto.getNodes().isEmpty();
        if (!hasBpmn && !hasJson) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "bpmnXml / nodes 至少二选一");
        }

        List<FlowNodeDO> nodes = new ArrayList<>();
        List<FlowSkipDO> skips = new ArrayList<>();

        if (hasBpmn) {
            // 模式 A：标准 BPMN 2.0 XML
            BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
            // 校验：flowCode 必须与 BPMN process id 一致（或缺失时不强制）
            if (StringUtils.hasText(bpmnModel.getProcessId())
                    && !bpmnModel.getProcessId().equals(dto.getFlowCode())) {
                throw new BizException(BizErrorCode.BAD_REQUEST,
                        "BPMN process id 与 flowCode 不一致: bpmn=" + bpmnModel.getProcessId()
                                + " dto=" + dto.getFlowCode());
            }
            // 若 dto.flowName 为空，用 BPMN process name
            if (!StringUtils.hasText(dto.getFlowName()) || dto.getFlowName().equals(dto.getFlowCode())) {
                dto.setFlowName(bpmnModel.getProcessName());
            }
            nodes.addAll(bpmnModel.getNodes());
            skips.addAll(bpmnModel.getSkips());
            // P3-1: 自动注入 BPMNDI 坐标到节点 coordinate 字段（覆盖现有值）
            Map<String, BpmnModel.NodeCoordinate> nodeCoords = bpmnModel.getNodeCoordinates();
            if (nodeCoords != null && !nodeCoords.isEmpty()) {
                for (FlowNodeDO n : nodes) {
                    BpmnModel.NodeCoordinate coord = nodeCoords.get(n.getNodeCode());
                    if (coord != null) {
                        n.setCoordinate(JsonHelper.toJson(Map.of(
                                "x", coord.getX(),
                                "y", coord.getY(),
                                "width", coord.getWidth(),
                                "height", coord.getHeight()
                        )));
                    }
                }
                log.info("[Flow] 从 BPMNDI 注入节点坐标: defId-pending count={}", nodeCoords.size());
            }
        } else {
            // 模式 B：轻量 JSON
            for (FlowDeployProcessDTO.FlowNodeDTO n : dto.getNodes()) {
                FlowNodeDO node = new FlowNodeDO();
                node.setNodeCode(n.getNodeCode());
                node.setNodeName(n.getNodeName() == null ? n.getNodeCode() : n.getNodeName());
                node.setNodeType(n.getNodeType() == null
                        ? FlowNodeType.APPROVAL.getCode() : n.getNodeType());
                node.setPermissionFlag(n.getPermissionFlag());
                node.setSkipAnyNode(n.getSkipAnyNode());
                nodes.add(node);
            }
            // 必须含开始节点
            boolean hasStart = nodes.stream()
                    .anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
            if (!hasStart) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "流程定义必须包含开始节点（nodeType=0）");
            }
            // 节点编码唯一
            long uniqueCount = nodes.stream()
                    .map(FlowNodeDO::getNodeCode)
                    .distinct()
                    .count();
            if (uniqueCount != nodes.size()) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "节点编码 nodeCode 必须唯一");
            }
            if (dto.getSkips() != null) {
                for (FlowDeployProcessDTO.FlowSkipDTO s : dto.getSkips()) {
                    FlowSkipDO skip = new FlowSkipDO();
                    skip.setSkipName(s.getSkipName());
                    skip.setSkipType(StringUtils.hasText(s.getSkipType())
                            ? s.getSkipType() : FlowSkipType.PASS.name());
                    skip.setSkipCondition(s.getSkipCondition());
                    skip.setNextNodeCode(s.getToNodeCode());
                    skip.setExt("{\"sourceRef\":\"" + s.getFromNodeCode() + "\"}");
                    skips.add(skip);
                }
            }
        }

        // P2-1: 流程图结构校验（连通性/死节点/环路）
        graphValidator.validate(nodes, skips);

        // 3. 写入定义
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setFlowCode(dto.getFlowCode());
        def.setFlowName(dto.getFlowName());
        def.setCategory(dto.getCategory());
        def.setFlowVersion(version);
        def.setModelValue("CLASSICS");
        def.setFormCustom("N");
        def.setFormPath(dto.getFormPath());
        def.setActivityStatus(1);
        def.setIsPublish(0);
        def.setDescription(dto.getDescription());
        def.setTenantId(tenantId);
        def.setProviderTraceId(dto.getProviderTraceId());
        definitionMapper.insert(def);
        String definitionId = def.getId();

        // 4. 写入节点
        for (FlowNodeDO node : nodes) {
            node.setDefinitionId(definitionId);
            node.setFlowCode(dto.getFlowCode());
            node.setTenantId(tenantId);
            node.setProviderTraceId(dto.getProviderTraceId());
            nodeMapper.insert(node);
        }

        // 5. 写入跳转
        for (FlowSkipDO skip : skips) {
            skip.setDefinitionId(definitionId);
            skip.setFlowCode(dto.getFlowCode());
            skip.setTenantId(tenantId);
            skip.setProviderTraceId(dto.getProviderTraceId());
            skipMapper.insert(skip);
        }

        log.info("[Flow] 部署流程成功: code={} version={} defId={} mode={} nodes={} skips={}",
                dto.getFlowCode(), version, definitionId,
                hasBpmn ? "BPMN" : "JSON",
                nodes.size(), skips.size());
        // P1: 部署新版本后主动清除该定义的缓存（防御性，避免遗留脏数据）
        flowDefinitionCacheService.evict(definitionId);
        return definitionId;
    }

    @Override
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void publish(String definitionId) {
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        definitionMapper.publish(definitionId, 1);
        log.info("[Flow] 发布流程: defId={}", definitionId);
    }

    @Override
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void deprecate(String definitionId) {
        definitionMapper.publish(definitionId, 9);
        log.info("[Flow] 停用流程: defId={}", definitionId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            key = "#flowCode + ':' + #version + ':' + #tenantId", unless = "#result == null")
    public FlowDefinitionDO getPublished(String flowCode, String version, String tenantId) {
        if (!StringUtils.hasText(version)) {
            version = "1.0";
        }
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        return definitionMapper.selectPublished(flowCode, version, tid);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.FLOW_DEF_LATEST_CACHE,
            key = "#flowCode + ':' + #tenantId", unless = "#result == null")
    public FlowDefinitionDO getLatestByCode(String flowCode, String tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        return definitionMapper.selectLatestByCode(flowCode, tid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowDefinitionDO> page(int pageNo, int pageSize, String category, String flowCode) {
        Page<FlowDefinitionDO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<FlowDefinitionDO> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(category), FlowDefinitionDO::getCategory, category)
                .like(StringUtils.hasText(flowCode), FlowDefinitionDO::getFlowCode, flowCode)
                .eq(FlowDefinitionDO::getActivityStatus, 1)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt);
        return definitionMapper.selectPage(page, w).getRecords();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(String definitionId) {
        // P2-21: 组装 definition + nodes + skips
        FlowDefinitionDO definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            return null;
        }
        List<FlowNodeDO> nodes = nodeMapper.selectByDefinitionId(definitionId);
        List<FlowSkipDO> skips = skipMapper.selectByDefinitionId(definitionId);
        Map<String, Object> result = new HashMap<>();
        result.put("definition", definition);
        result.put("nodes", nodes);
        result.put("skips", skips);
        return result;
    }

    // ============================== P2-27: 版本切换 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void switchActiveVersion(String flowCode, String definitionId, String tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "flowCode 不能为空");
        }
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        if (!flowCode.equals(def.getFlowCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "flowCode 不匹配: 期望=" + flowCode + " 实际=" + def.getFlowCode());
        }
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        // 失效同 flowCode 的其他已发布版本
        definitionMapper.deactivateByFlowCode(flowCode, definitionId, tid);
        // 激活目标版本
        definitionMapper.publish(definitionId, 1);
        log.info("[Flow] 切换流程定义版本: flowCode={} → defId={} tenantId={}",
                flowCode, definitionId, tid);
    }

    // ============================== P2-28: 启用/停用 ==============================

    @Override
    public void enable(String definitionId) {
        definitionMapper.updateActivityStatus(definitionId, 1);
        log.info("[Flow] 启用流程定义: defId={}", definitionId);
    }

    @Override
    public void disable(String definitionId) {
        definitionMapper.updateActivityStatus(definitionId, 0);
        log.info("[Flow] 停用流程定义: defId={}", definitionId);
    }

    // ============================== P2-40: 节点坐标更新 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNodeCoordinate(String definitionId, String nodeCode, String coordinate) {
        if (definitionId == null || !StringUtils.hasText(nodeCode)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "definitionId/nodeCode 不能为空");
        }
        FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        node.setCoordinate(coordinate);
        nodeMapper.updateById(node);
        // 节点数据变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 更新节点坐标: defId={} node={} coordinate={}",
                definitionId, nodeCode, coordinate);
    }

    // ============================== P2-41: 流程定义草稿编辑 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
            CacheConstants.FLOW_DEF_LATEST_CACHE}, allEntries = true)
    public void updateDefinition(String definitionId, FlowDeployProcessDTO dto) {
        if (definitionId == null || dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "definitionId/dto 不能为空");
        }
        // 1. 校验定义存在且未发布（只有未发布定义才能编辑）
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        if (def.getIsPublish() != null && def.getIsPublish() == 1) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "已发布的流程定义不可编辑，请创建新版本: " + definitionId);
        }

        // 2. 更新定义元数据（不修改 version 和 flowCode — 核心标识不可变）
        if (StringUtils.hasText(dto.getFlowName())) {
            def.setFlowName(dto.getFlowName());
        }
        if (StringUtils.hasText(dto.getCategory())) {
            def.setCategory(dto.getCategory());
        }
        if (dto.getDescription() != null) {
            def.setDescription(dto.getDescription());
        }
        if (StringUtils.hasText(dto.getFormPath())) {
            def.setFormPath(dto.getFormPath());
        }
        // ext 字段透传（FlowDeployProcessDTO 暂无 ext 字段，跳过）
        definitionMapper.updateById(def);

        // 3. 如果 dto 中包含 nodes/skips，先删除旧节点/跳转，再插入新的
        boolean hasNodes = dto.getNodes() != null && !dto.getNodes().isEmpty();
        boolean hasSkips = dto.getSkips() != null && !dto.getSkips().isEmpty();
        boolean hasBpmn = StringUtils.hasText(dto.getBpmnXml());
        if (hasBpmn || hasNodes || hasSkips) {
            skipMapper.deleteByDefinitionId(definitionId);
            nodeMapper.deleteByDefinitionId(definitionId);

            List<FlowNodeDO> nodes = new ArrayList<>();
            List<FlowSkipDO> skips = new ArrayList<>();

            if (hasBpmn) {
                // BPMN 模式：解析 XML
                BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
                nodes.addAll(bpmnModel.getNodes());
                skips.addAll(bpmnModel.getSkips());
            } else {
                // JSON 模式：从 DTO 构造节点
                for (FlowDeployProcessDTO.FlowNodeDTO n : dto.getNodes()) {
                    FlowNodeDO node = new FlowNodeDO();
                    node.setNodeCode(n.getNodeCode());
                    node.setNodeName(n.getNodeName() == null ? n.getNodeCode() : n.getNodeName());
                    node.setNodeType(n.getNodeType() == null
                            ? FlowNodeType.APPROVAL.getCode() : n.getNodeType());
                    node.setPermissionFlag(n.getPermissionFlag());
                    node.setSkipAnyNode(n.getSkipAnyNode());
                    nodes.add(node);
                }
                if (dto.getSkips() != null) {
                    for (FlowDeployProcessDTO.FlowSkipDTO s : dto.getSkips()) {
                        FlowSkipDO skip = new FlowSkipDO();
                        skip.setSkipName(s.getSkipName());
                        skip.setSkipType(StringUtils.hasText(s.getSkipType())
                                ? s.getSkipType() : FlowSkipType.PASS.name());
                        skip.setSkipCondition(s.getSkipCondition());
                        skip.setNextNodeCode(s.getToNodeCode());
                        skip.setExt("{\"sourceRef\":\"" + s.getFromNodeCode() + "\"}");
                        skips.add(skip);
                    }
                }
            }

            // 写入节点
            for (FlowNodeDO node : nodes) {
                node.setDefinitionId(definitionId);
                node.setFlowCode(def.getFlowCode());
                node.setTenantId(def.getTenantId());
                node.setProviderTraceId(dto.getProviderTraceId());
                nodeMapper.insert(node);
            }
            // 写入跳转
            for (FlowSkipDO skip : skips) {
                skip.setDefinitionId(definitionId);
                skip.setFlowCode(def.getFlowCode());
                skip.setTenantId(def.getTenantId());
                skip.setProviderTraceId(dto.getProviderTraceId());
                skipMapper.insert(skip);
            }
        }

        // P1: 节点/跳转可能被重写，清除缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 编辑流程定义草稿: defId={} flowCode={}", definitionId, def.getFlowCode());
    }

    // ============================== GAP-V2-06: 导入/导出 ==============================

    @Override
    @Transactional(readOnly = true)
    public String exportDefinition(String definitionId) {
        Map<String, Object> detail = getDetail(definitionId);
        if (detail == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        return JSON.toJSONString(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importDefinition(String json, String tenantId) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "导入 JSON 不能为空");
        }
        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (Exception e) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "JSON 解析失败: " + e.getMessage());
        }
        if (root == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "JSON 内容为空");
        }

        // 1. 提取 definition 元数据
        JSONObject defJson = root.getJSONObject("definition");
        if (defJson == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "JSON 缺少 definition 字段");
        }
        String flowCode = defJson.getString("flowCode");
        String flowName = defJson.getString("flowName");
        if (!StringUtils.hasText(flowCode) || !StringUtils.hasText(flowName)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "definition 中 flowCode/flowName 不能为空");
        }

        // 2. 构建 FlowDeployProcessDTO
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode(flowCode);
        dto.setFlowName(flowName);
        dto.setVersion(defJson.getString("version"));
        dto.setCategory(defJson.getString("category"));
        dto.setDescription(defJson.getString("description"));
        dto.setFormPath(defJson.getString("formPath"));
        dto.setTenantId(tenantId);
        dto.setProviderTraceId(defJson.getString("providerTraceId"));

        // 3. 提取 nodes
        JSONArray nodesJson = root.getJSONArray("nodes");
        if (nodesJson != null && !nodesJson.isEmpty()) {
            List<FlowDeployProcessDTO.FlowNodeDTO> nodes = new ArrayList<>();
            for (int i = 0; i < nodesJson.size(); i++) {
                JSONObject n = nodesJson.getJSONObject(i);
                FlowDeployProcessDTO.FlowNodeDTO node = new FlowDeployProcessDTO.FlowNodeDTO();
                node.setNodeCode(n.getString("nodeCode"));
                node.setNodeName(n.getString("nodeName"));
                node.setNodeType(n.getInteger("nodeType"));
                node.setPermissionFlag(n.getString("permissionFlag"));
                node.setSkipAnyNode(n.getString("skipAnyNode"));
                nodes.add(node);
            }
            dto.setNodes(nodes);
        }

        // 4. 提取 skips（从 ext.sourceRef 还原 fromNodeCode）
        JSONArray skipsJson = root.getJSONArray("skips");
        if (skipsJson != null && !skipsJson.isEmpty()) {
            List<FlowDeployProcessDTO.FlowSkipDTO> skips = new ArrayList<>();
            for (int i = 0; i < skipsJson.size(); i++) {
                JSONObject s = skipsJson.getJSONObject(i);
                FlowDeployProcessDTO.FlowSkipDTO skip = new FlowDeployProcessDTO.FlowSkipDTO();
                skip.setSkipName(s.getString("skipName"));
                skip.setSkipType(s.getString("skipType"));
                skip.setSkipCondition(s.getString("skipCondition"));
                skip.setToNodeCode(s.getString("nextNodeCode"));
                // 从 ext 字段还原 fromNodeCode
                String ext = s.getString("ext");
                if (StringUtils.hasText(ext)) {
                    try {
                        JSONObject extJson = JSON.parseObject(ext);
                        if (extJson != null) {
                            skip.setFromNodeCode(extJson.getString("sourceRef"));
                        }
                    } catch (Exception e) {
                        log.warn("[Flow] 导入跳转 ext 解析失败: skipName={} err={}",
                                s.getString("skipName"), e.getMessage());
                    }
                }
                skips.add(skip);
            }
            dto.setSkips(skips);
        }

        // 5. 调用 deploy 创建为草稿（isPublish=0）
        String newDefinitionId = deploy(dto);
        log.info("[Flow] 导入流程定义成功: flowCode={} version={} newDefId={}",
                dto.getFlowCode(), dto.getVersion(), newDefinitionId);
        return newDefinitionId;
    }

    // ============================== GAP-V2-01: 设计器数据 API ==============================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDesignerData(String definitionId) {
        Map<String, Object> detail = getDetail(definitionId);
        if (detail == null) {
            return null;
        }
        // 在 getDetail 基础上增加 edges 格式（供前端 VueFlow/LogicFlow 直接使用）
        Map<String, Object> result = new LinkedHashMap<>(detail);
        @SuppressWarnings("unchecked")
        List<FlowSkipDO> skips = (List<FlowSkipDO>) detail.get("skips");
        if (skips != null) {
            List<Map<String, Object>> edges = new ArrayList<>();
            for (FlowSkipDO skip : skips) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", skip.getId());
                // sourceRef 存储在 ext JSON 中
                String source = null;
                if (StringUtils.hasText(skip.getExt())) {
                    try {
                        JSONObject extJson = JSON.parseObject(skip.getExt());
                        source = extJson != null ? extJson.getString("sourceRef") : null;
                    } catch (Exception e) { log.warn("解析skip节点ext JSON失败: {}", e.getMessage(), e); }
                }
                edge.put("source", source);
                edge.put("target", skip.getNextNodeCode());
                edge.put("label", skip.getSkipName());
                edge.put("condition", skip.getSkipCondition());
                edge.put("skipType", skip.getSkipType());
                edges.add(edge);
            }
            result.put("edges", edges);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDesignerData(String definitionId, Map<String, Object> designerData) {
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        if (def.getIsPublish() != null && def.getIsPublish() == 1) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已发布的流程定义不可编辑，请先创建新版本");
        }

        // 1. 批量更新节点坐标 + 属性
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) designerData.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> nodeData : nodes) {
                String nodeCode = (String) nodeData.get("nodeCode");
                if (nodeCode == null) {
                    continue;
                }
                // 更新坐标
                Object coord = nodeData.get("coordinate");
                if (coord != null) {
                    String coordStr = coord instanceof String
                            ? (String) coord : JSON.toJSONString(coord);
                    FlowNodeDO nodeForCoord = nodeMapper.selectByCode(definitionId, nodeCode);
                    if (nodeForCoord != null) {
                        nodeForCoord.setCoordinate(coordStr);
                        nodeMapper.updateById(nodeForCoord);
                    }
                }
                // 更新节点名称（如前端修改了）
                Object nodeName = nodeData.get("nodeName");
                if (nodeName != null) {
                    FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
                    if (node != null) {
                        node.setNodeName((String) nodeName);
                        Object permFlag = nodeData.get("permissionFlag");
                        if (permFlag != null) {
                            node.setPermissionFlag((String) permFlag);
                        }
                        Object ext = nodeData.get("ext");
                        if (ext != null) {
                            node.setExt(ext instanceof String ? (String) ext : JSON.toJSONString(ext));
                        }
                        nodeMapper.updateById(node);
                    }
                }
            }
        }

        // 2. 批量更新边（skips）— 目前仅支持坐标和属性更新，不支持增删边
        // 边的增删需要通过 updateDefinition 端点处理
        // 节点数据批量变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 设计器数据已保存: definitionId={} nodes={}",
                definitionId, nodes != null ? nodes.size() : 0);
    }

    // ============================== GAP-V2-02: 表单字段配置 ==============================

    @Override
    @Transactional(readOnly = true)
    public String getFormConfig(String definitionId, String nodeCode) {
        FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        return node.getFormFieldsConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFormConfig(String definitionId, String nodeCode, String formFieldsConfig) {
        FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        node.setFormFieldsConfig(formFieldsConfig);
        nodeMapper.updateById(node);
        // 节点数据变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] 表单字段配置已保存: definitionId={} nodeCode={}",
                definitionId, nodeCode);
    }

    // ============================== P1-2: SLA 节点级配置 ==============================

    @Override
    @Transactional(readOnly = true)
    public String getSlaConfig(String definitionId, String nodeCode) {
        FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        return node.getSlaConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSlaConfig(String definitionId, String nodeCode, String slaConfig) {
        FlowNodeDO node = nodeMapper.selectByCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        node.setSlaConfig(slaConfig);
        nodeMapper.updateById(node);
        // 节点数据变更，清除该定义的本地节点/跳转缓存避免脏读
        flowDefinitionCacheService.evict(definitionId);
        log.info("[Flow] SLA 配置已保存: definitionId={} nodeCode={} slaConfig={}",
                definitionId, nodeCode, slaConfig);
    }

    // ============================== 版本历史与差异对比 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listVersions(String definitionId) {
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        String tenantId = def.getTenantId() != null ? def.getTenantId() : "1";
        List<FlowDefinitionDO> versions = definitionMapper.selectByFlowCode(def.getFlowCode(), tenantId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowDefinitionDO v : versions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", v.getId());
            map.put("version", v.getFlowVersion());
            map.put("flowName", v.getFlowName());
            map.put("isPublish", v.getIsPublish());
            map.put("activityStatus", v.getActivityStatus());
            map.put("category", v.getCategory());
            map.put("description", v.getDescription());
            map.put("createdAt", v.getCreatedAt());
            map.put("updatedAt", v.getUpdatedAt());
            result.add(map);
        }
        log.info("[Flow] 查询版本历史: flowCode={} count={}",
                def.getFlowCode(), result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> diffVersions(String definitionId, Integer version1, Integer version2) {
        // 1. 获取基础定义，找到 flowCode
        FlowDefinitionDO baseDef = definitionMapper.selectById(definitionId);
        if (baseDef == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        String tenantId = baseDef.getTenantId() != null ? baseDef.getTenantId() : "1";

        // 2. 查找两个版本的定义
        List<FlowDefinitionDO> allVersions = definitionMapper.selectByFlowCode(
                baseDef.getFlowCode(), tenantId);
        String v1Str = String.valueOf(version1);
        String v2Str = String.valueOf(version2);
        FlowDefinitionDO defV1 = allVersions.stream()
                .filter(d -> v1Str.equals(d.getFlowVersion()))
                .findFirst().orElse(null);
        FlowDefinitionDO defV2 = allVersions.stream()
                .filter(d -> v2Str.equals(d.getFlowVersion()))
                .findFirst().orElse(null);

        if (defV1 == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "版本 " + version1 + " 不存在: flowCode=" + baseDef.getFlowCode());
        }
        if (defV2 == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "版本 " + version2 + " 不存在: flowCode=" + baseDef.getFlowCode());
        }

        // 3. 获取两个版本的节点和跳转
        List<FlowNodeDO> nodesV1 = nodeMapper.selectByDefinitionId(defV1.getId());
        List<FlowNodeDO> nodesV2 = nodeMapper.selectByDefinitionId(defV2.getId());
        List<FlowSkipDO> skipsV1 = skipMapper.selectByDefinitionId(defV1.getId());
        List<FlowSkipDO> skipsV2 = skipMapper.selectByDefinitionId(defV2.getId());

        // 4. 构建节点 nodeCode -> FlowNodeDO 映射
        Map<String, FlowNodeDO> nodeMapV1 = nodesV1.stream()
                .collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));
        Map<String, FlowNodeDO> nodeMapV2 = nodesV2.stream()
                .collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));

        // 5. 对比节点差异
        List<Map<String, Object>> addedNodes = new ArrayList<>();
        List<Map<String, Object>> removedNodes = new ArrayList<>();
        List<Map<String, Object>> modifiedNodes = new ArrayList<>();

        // v2 有而 v1 没有 -> 新增
        for (Map.Entry<String, FlowNodeDO> entry : nodeMapV2.entrySet()) {
            if (!nodeMapV1.containsKey(entry.getKey())) {
                FlowNodeDO n = entry.getValue();
                addedNodes.add(Map.of("nodeCode", n.getNodeCode(),
                        "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
            }
        }
        // v1 有而 v2 没有 -> 删除
        for (Map.Entry<String, FlowNodeDO> entry : nodeMapV1.entrySet()) {
            if (!nodeMapV2.containsKey(entry.getKey())) {
                FlowNodeDO n = entry.getValue();
                removedNodes.add(Map.of("nodeCode", n.getNodeCode(),
                        "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
            }
        }
        // 两者都有 -> 检查修改
        for (Map.Entry<String, FlowNodeDO> entry : nodeMapV1.entrySet()) {
            String code = entry.getKey();
            if (nodeMapV2.containsKey(code)) {
                FlowNodeDO n1 = entry.getValue();
                FlowNodeDO n2 = nodeMapV2.get(code);
                Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
                if (!Objects.equals(n1.getNodeName(), n2.getNodeName())) {
                    changes.put("nodeName", Map.of("old",
                            n1.getNodeName() != null ? n1.getNodeName() : "",
                            "new", n2.getNodeName() != null ? n2.getNodeName() : ""));
                }
                if (!Objects.equals(n1.getNodeType(), n2.getNodeType())) {
                    changes.put("nodeType", Map.of("old",
                            n1.getNodeType() != null ? n1.getNodeType() : "",
                            "new", n2.getNodeType() != null ? n2.getNodeType() : ""));
                }
                if (!Objects.equals(n1.getPermissionFlag(), n2.getPermissionFlag())) {
                    changes.put("permissionFlag", Map.of("old",
                            n1.getPermissionFlag() != null ? n1.getPermissionFlag() : "",
                            "new", n2.getPermissionFlag() != null ? n2.getPermissionFlag() : ""));
                }
                if (!changes.isEmpty()) {
                    Map<String, Object> modEntry = new LinkedHashMap<>();
                    modEntry.put("nodeCode", code);
                    modEntry.put("changes", changes);
                    modifiedNodes.add(modEntry);
                }
            }
        }

        // 6. 构建连线 key 映射（sourceRef -> targetNodeCode）
        // sourceRef 存储在 ext JSON 的 sourceRef 字段中
        Map<String, FlowSkipDO> skipMapV1 = buildSkipKeyMap(skipsV1);
        Map<String, FlowSkipDO> skipMapV2 = buildSkipKeyMap(skipsV2);

        // 7. 对比连线差异
        List<Map<String, Object>> addedSkips = new ArrayList<>();
        List<Map<String, Object>> removedSkips = new ArrayList<>();

        for (Map.Entry<String, FlowSkipDO> entry : skipMapV2.entrySet()) {
            if (!skipMapV1.containsKey(entry.getKey())) {
                FlowSkipDO s = entry.getValue();
                addedSkips.add(skipToMap(s));
            }
        }
        for (Map.Entry<String, FlowSkipDO> entry : skipMapV1.entrySet()) {
            if (!skipMapV2.containsKey(entry.getKey())) {
                FlowSkipDO s = entry.getValue();
                removedSkips.add(skipToMap(s));
            }
        }

        // 8. 组装结果
        Map<String, Object> nodeChanges = new LinkedHashMap<>();
        nodeChanges.put("added", addedNodes);
        nodeChanges.put("removed", removedNodes);
        nodeChanges.put("modified", modifiedNodes);

        Map<String, Object> skipChanges = new LinkedHashMap<>();
        skipChanges.put("added", addedSkips);
        skipChanges.put("removed", removedSkips);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version1", version1);
        result.put("version2", version2);
        result.put("nodeChanges", nodeChanges);
        result.put("skipChanges", skipChanges);

        log.info("[Flow] 版本差异对比: flowCode={} v1={} v2={} "
                        + "nodeAdded={} nodeRemoved={} nodeModified={} "
                        + "skipAdded={} skipRemoved={}",
                baseDef.getFlowCode(), version1, version2,
                addedNodes.size(), removedNodes.size(), modifiedNodes.size(),
                addedSkips.size(), removedSkips.size());

        return result;
    }

    /**
     * 构建连线 key 映射：sourceRef + "->" + nextNodeCode
     */
    private Map<String, FlowSkipDO> buildSkipKeyMap(List<FlowSkipDO> skips) {
        Map<String, FlowSkipDO> map = new LinkedHashMap<>();
        for (FlowSkipDO skip : skips) {
            String key = buildSkipKey(skip);
            if (key != null) {
                map.put(key, skip);
            }
        }
        return map;
    }

    /**
     * 从 ext JSON 中提取 sourceRef，拼接 key
     */
    private String buildSkipKey(FlowSkipDO skip) {
        String sourceRef = null;
        if (StringUtils.hasText(skip.getExt())) {
            try {
                JSONObject extJson = JSON.parseObject(skip.getExt());
                sourceRef = extJson != null ? extJson.getString("sourceRef") : null;
            } catch (Exception ignored) {
                // ignore parse error
            }
        }
        if (sourceRef != null && skip.getNextNodeCode() != null) {
            return sourceRef + "->" + skip.getNextNodeCode();
        }
        return skip.getId() != null ? String.valueOf(skip.getId()) : null;
    }

    /**
     * 将连线转为 Map 表示
     */
    private Map<String, Object> skipToMap(FlowSkipDO skip) {
        String sourceRef = null;
        if (StringUtils.hasText(skip.getExt())) {
            try {
                JSONObject extJson = JSON.parseObject(skip.getExt());
                sourceRef = extJson != null ? extJson.getString("sourceRef") : null;
            } catch (Exception ignored) {
                // ignore
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sourceRef", sourceRef != null ? sourceRef : "");
        map.put("nextNodeCode", skip.getNextNodeCode() != null ? skip.getNextNodeCode() : "");
        map.put("skipName", skip.getSkipName() != null ? skip.getSkipName() : "");
        map.put("skipType", skip.getSkipType() != null ? skip.getSkipType() : "");
        map.put("skipCondition", skip.getSkipCondition() != null ? skip.getSkipCondition() : "");
        return map;
    }

    // ============================== GAP-P1-6: BPMN 部署包 .zip 批量导入 ==============================

    /**
     * GAP-P1-6: 从 BPMN 部署包 .zip 批量导入流程定义。
     *
     * <p>对标 Activiti/Flowable 的 {@code repositoryService.createDeployment().addZipInputStream()}。
     * 遍历 zip 内的 {@code .bpmn} / {@code .bpmn20.xml} 文件，逐个解析并委托 {@link #deploy} 入库。
     * 单个文件失败不影响其他文件（通过 self 代理调用 deploy，每个文件独立事务）。
     *
     * <p>flowCode 取自 BPMN process id，flowName 取自 BPMN process name（缺失时回退为文件名）。
     * 版本号默认 "1.0"，如已存在同 flowCode + version 的定义则该文件记为失败并跳过。
     *
     * @param zipBytes zip 文件字节数组
     * @param tenantId 租户 ID（可空，默认从 SecurityContext 获取）
     * @return Map 包含 successCount（成功数）和 failedItems（失败列表，每项含 fileName + reason）
     */
    @Override
    public Map<String, Object> batchDeployFromZip(byte[] zipBytes, String tenantId) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "zip 文件内容为空");
        }
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");

        int successCount = 0;
        List<Map<String, String>> failedItems = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = entry.getName();
                // 仅处理 .bpmn / .bpmn20.xml 文件（大小写不敏感）
                String lowerName = fileName.toLowerCase();
                if (!lowerName.endsWith(".bpmn") && !lowerName.endsWith(".bpmn20.xml")) {
                    continue;
                }
                try {
                    String bpmnXml = new String(readAllBytes(zis), StandardCharsets.UTF_8);
                    // 先解析一次获取 processId（作为 flowCode）和 processName（作为 flowName）
                    BpmnModel model = bpmnXmlParser.parse(bpmnXml);
                    String flowCode = model.getProcessId();
                    String flowName = StringUtils.hasText(model.getProcessName())
                            ? model.getProcessName() : extractBaseName(fileName);

                    if (!StringUtils.hasText(flowCode)) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "BPMN 文件缺少 process id: " + fileName);
                    }

                    FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
                    dto.setFlowCode(flowCode);
                    dto.setFlowName(flowName);
                    dto.setVersion("1.0");
                    dto.setBpmnXml(bpmnXml);
                    dto.setTenantId(tid);
                    // 通过 self 代理调用，确保 deploy 的 @Transactional 生效（独立事务）
                    self.deploy(dto);
                    successCount++;
                    log.info("[Flow] zip 批量导入成功: fileName={} flowCode={}", fileName, flowCode);
                } catch (Exception e) {
                    Map<String, String> fail = new LinkedHashMap<>();
                    fail.put("fileName", fileName);
                    fail.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    failedItems.add(fail);
                    log.warn("[Flow] zip 批量导入失败: fileName={} reason={}", fileName, e.getMessage());
                } finally {
                    zis.closeEntry();
                }
            }
        } catch (Exception e) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "zip 文件解析失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failedItems", failedItems);
        log.info("[Flow] zip 批量导入完成: success={} failed={}", successCount, failedItems.size());
        return result;
    }

    /** 读取 ZipInputStream 当前 entry 的全部字节（不关闭流） */
    private byte[] readAllBytes(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    /** 从 zip entry 路径中提取文件名（去掉目录和扩展名） */
    private String extractBaseName(String fileName) {
        String name = fileName;
        int slashIdx = name.lastIndexOf('/');
        if (slashIdx >= 0) {
            name = name.substring(slashIdx + 1);
        }
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            name = name.substring(0, dotIdx);
        }
        return name;
    }

    // ============================== P2-4: 设计器协同编辑锁定 ==============================

    /**
     * P2-4: 加锁流程定义。
     *
     * <p>采用 CAS（Compare-And-Swap）乐观锁实现，保证多用户并发加锁的强一致性：
     * <ol>
     *   <li>未锁定（lockedBy IS NULL）→ CAS 成功</li>
     *   <li>同一人持锁（lockedBy = userId）→ CAS 续约成功</li>
     *   <li>他人持锁但已超时（lockedAt &lt; timeoutExpired）→ CAS 抢占成功</li>
     *   <li>他人持锁且未超时 → CAS 失败，抛 BizException</li>
     * </ol>
     *
     * <p>使用 {@link FlowDefinitionMapper#casLock} 的单条 UPDATE SQL 完成判定 + 更新，
     * 避免"读-判-写"竞态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockDefinition(String definitionId, String userId) {
        if (!StringUtils.hasText(definitionId) || !StringUtils.hasText(userId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_d6e7f8a9");
        }
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_e7f8a9b0", definitionId);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutExpired = now.minusMinutes(lockTimeoutMinutes);

        // CAS 加锁：expectedOldBy = userId 用于"同号续约"场景
        // SQL 条件：(locked_by IS NULL OR locked_by = userId OR locked_at < timeoutExpired)
        //          AND version = #{version}
        // 这里 expectedOldBy 传 userId，因为若是同一人持锁应允许续约
        int affected = definitionMapper.casLock(
                definitionId, userId, now, userId, timeoutExpired, def.getVersion());

        if (affected == 1) {
            log.info("[Flow] 设计器加锁成功: defId={} userId={} timeout={}min",
                    definitionId, userId, lockTimeoutMinutes);
            return true;
        }

        // CAS 失败：要么 version 不匹配（并发更新），要么锁被他人持有且未超时
        FlowDefinitionDO latest = definitionMapper.selectById(definitionId);
        if (latest == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_e7f8a9b0", definitionId);
        }
        String holder = latest.getLockedBy();
        if (StringUtils.hasText(holder) && !holder.equals(userId)) {
            // 检查是否其实已超时（理论上 SQL 应能命中，但 version 不匹配会阻塞）
            boolean expired = latest.getLockedAt() != null
                    && latest.getLockedAt().isBefore(timeoutExpired);
            if (expired) {
                // 已超时但 CAS 失败 → 因 version 变化导致，重试一次
                log.warn("[Flow] 设计器加锁重试（锁已超时但 version 变化）: defId={} holder={}",
                        definitionId, holder);
                int retry = definitionMapper.casLock(
                        definitionId, userId, now, userId, timeoutExpired, latest.getVersion());
                if (retry == 1) {
                    log.info("[Flow] 设计器加锁成功（重试）: defId={} userId={} 抢占自={}",
                            definitionId, userId, holder);
                    return true;
                }
            }
            // 锁被他人持有且未超时
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_f8a9b0c1", holder);
        }
        // 走到这里说明是并发 version 变化导致，按并发冲突处理
        throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                "error.workflow.msg_a9b0c1d2");
    }

    /**
     * P2-4: 解锁流程定义。
     *
     * <p>仅持锁人本人可解锁；他人持锁或未锁定时抛 BizException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockDefinition(String definitionId, String userId) {
        if (!StringUtils.hasText(definitionId) || !StringUtils.hasText(userId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_d6e7f8a9");
        }
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_e7f8a9b0", definitionId);
        }

        // 未锁定直接返回成功（幂等）
        if (!StringUtils.hasText(def.getLockedBy())) {
            log.debug("[Flow] 设计器解锁：当前未锁定，幂等返回 defId={}", definitionId);
            return true;
        }

        // CAS 解锁：仅持锁人可解锁
        int affected = definitionMapper.casUnlock(definitionId, userId, def.getVersion());
        if (affected == 1) {
            log.info("[Flow] 设计器解锁成功: defId={} userId={}", definitionId, userId);
            return true;
        }

        // CAS 失败：要么非持锁人，要么 version 变化
        FlowDefinitionDO latest = definitionMapper.selectById(definitionId);
        if (latest == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_e7f8a9b0", definitionId);
        }
        String holder = latest.getLockedBy();
        if (StringUtils.hasText(holder) && !holder.equals(userId)) {
            throw new BizException(BizErrorCode.FORBIDDEN,
                    "error.workflow.msg_b1c2d3e4", holder);
        }
        // 此时 holder = userId 或 holder 已被清空（并发已解锁）→ 视为成功
        log.info("[Flow] 设计器解锁：锁已被并发清空，视为成功 defId={} userId={}",
                definitionId, userId);
        return true;
    }

    /**
     * P2-4: 查询流程定义的锁定状态。
     */
    @Override
    public Map<String, Object> getLockStatus(String definitionId) {
        if (!StringUtils.hasText(definitionId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_d6e7f8a9");
        }
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        boolean locked = StringUtils.hasText(def.getLockedBy());
        boolean expired = false;
        if (locked && def.getLockedAt() != null) {
            LocalDateTime timeoutExpired = LocalDateTime.now().minusMinutes(lockTimeoutMinutes);
            expired = def.getLockedAt().isBefore(timeoutExpired);
        }
        result.put("locked", locked);
        result.put("lockedBy", def.getLockedBy());
        result.put("lockedAt", def.getLockedAt());
        result.put("expired", expired);
        return result;
    }

    // ============================== P2-5: 变更影响分析报告 ==============================

    /**
     * P2-5: 变更影响分析报告。
     *
     * <p>评估老版本定义升级到新版本对在途实例的影响，输出：
     * <ul>
     *   <li>版本差异（节点新增/删除/修改 + 跳转新增/删除）</li>
     *   <li>在途实例统计（总数 + 按节点分布）</li>
     *   <li>受影响实例识别（卡死节点 / 类型变更节点）</li>
     *   <li>风险等级（HIGH/MEDIUM/LOW/NONE）</li>
     *   <li>迁移建议（人工介入 / 等待自然完成 / 直接升级）</li>
     * </ul>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeMigrationImpact(String oldDefinitionId, String newDefinitionId) {
        if (!StringUtils.hasText(oldDefinitionId) || !StringUtils.hasText(newDefinitionId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_c2d3e4f5");
        }

        // 1. 校验两个定义存在
        FlowDefinitionDO oldDef = definitionMapper.selectById(oldDefinitionId);
        if (oldDef == null || (oldDef.getDeleted() != null && oldDef.getDeleted() == 1)) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_e7f8a9b0", oldDefinitionId);
        }
        FlowDefinitionDO newDef = definitionMapper.selectById(newDefinitionId);
        if (newDef == null || (newDef.getDeleted() != null && newDef.getDeleted() == 1)) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_e7f8a9b0", newDefinitionId);
        }
        // 校验同 flowCode
        if (!Objects.equals(oldDef.getFlowCode(), newDef.getFlowCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_d3e4f5a6");
        }

        // 2. 复用 diffVersions 计算版本差异
        Integer v1 = parseVersionInt(oldDef.getFlowVersion());
        Integer v2 = parseVersionInt(newDef.getFlowVersion());
        Map<String, Object> diff = diffVersions(oldDefinitionId, v1, v2);

        // 3. 统计老版本在途实例
        long runningTotal = instanceMapper.countRunningByDefinition(oldDefinitionId);
        List<Map<String, Object>> runningByNode = instanceMapper
                .selectRunningGroupByNode(oldDefinitionId);

        // 4. 识别受影响实例
        // 4.1 卡死实例：当前节点在老版本存在但在新版本被删除
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeChanges = (Map<String, Object>) diff.get("nodeChanges");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> removedNodes = (List<Map<String, Object>>)
                nodeChanges.get("removed");
        java.util.Set<String> removedNodeCodes = removedNodes.stream()
                .map(n -> String.valueOf(n.get("nodeCode")))
                .collect(Collectors.toSet());

        // 4.2 类型/审批人变更节点
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modifiedNodes = (List<Map<String, Object>>)
                nodeChanges.get("modified");

        List<Map<String, Object>> stuckInstances = new ArrayList<>();
        List<Map<String, Object>> affectedInstances = new ArrayList<>();
        for (Map<String, Object> node : runningByNode) {
            String nodeCode = String.valueOf(node.get("currentNodeCode"));
            long cnt = ((Number) node.get("cnt")).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("nodeCode", nodeCode);
            entry.put("currentNodeName", node.get("currentNodeName"));
            entry.put("instanceCount", cnt);
            if (removedNodeCodes.contains(nodeCode)) {
                entry.put("reason", "NODE_REMOVED");
                stuckInstances.add(entry);
            } else if (modifiedNodes.stream()
                    .anyMatch(m -> nodeCode.equals(String.valueOf(m.get("nodeCode"))))) {
                entry.put("reason", "NODE_MODIFIED");
                affectedInstances.add(entry);
            }
        }

        // 5. 计算风险等级
        // HIGH：有在途实例卡在已删除节点（无法继续流转）
        // MEDIUM：有在途实例在已修改节点（类型/审批人变化）或大量在途实例（>100）
        // LOW：有少量在途实例但节点未变更
        // NONE：无在途实例
        String riskLevel;
        if (runningTotal == 0) {
            riskLevel = "NONE";
        } else if (!stuckInstances.isEmpty()) {
            riskLevel = "HIGH";
        } else if (!affectedInstances.isEmpty() || runningTotal > 100) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        // 6. 生成迁移建议
        List<String> recommendations = buildRecommendations(
                riskLevel, runningTotal, stuckInstances, affectedInstances,
                removedNodes, modifiedNodes);

        // 7. 组装结果
        Map<String, Object> oldDefInfo = new LinkedHashMap<>();
        oldDefInfo.put("id", oldDef.getId());
        oldDefInfo.put("flowCode", oldDef.getFlowCode());
        oldDefInfo.put("flowName", oldDef.getFlowName());
        oldDefInfo.put("flowVersion", oldDef.getFlowVersion());

        Map<String, Object> newDefInfo = new LinkedHashMap<>();
        newDefInfo.put("id", newDef.getId());
        newDefInfo.put("flowCode", newDef.getFlowCode());
        newDefInfo.put("flowName", newDef.getFlowName());
        newDefInfo.put("flowVersion", newDef.getFlowVersion());

        Map<String, Object> runningInstances = new LinkedHashMap<>();
        runningInstances.put("total", runningTotal);
        runningInstances.put("byNode", runningByNode);

        Map<String, Object> impactedInstances = new LinkedHashMap<>();
        impactedInstances.put("stuckInstances", stuckInstances);
        impactedInstances.put("affectedInstances", affectedInstances);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("oldDefinition", oldDefInfo);
        result.put("newDefinition", newDefInfo);
        result.put("diff", diff);
        result.put("runningInstances", runningInstances);
        result.put("impactedInstances", impactedInstances);
        result.put("riskLevel", riskLevel);
        result.put("recommendations", recommendations);

        log.info("[Flow] 变更影响分析: oldDef={} newDef={} running={} stuck={} affected={} risk={}",
                oldDefinitionId, newDefinitionId, runningTotal,
                stuckInstances.size(), affectedInstances.size(), riskLevel);
        return result;
    }

    /**
     * 解析版本号字符串为整数（用于 diffVersions 调用）。
     *
     * @param versionStr 版本字符串（如 "1.0" / "2"）
     * @return 主版本号整数（如 1 / 2），无法解析时返回 0
     */
    private Integer parseVersionInt(String versionStr) {
        if (!StringUtils.hasText(versionStr)) {
            return 0;
        }
        try {
            // "1.0" → 取 "." 之前的部分；"2" → 直接转
            String main = versionStr.contains(".")
                    ? versionStr.substring(0, versionStr.indexOf('.'))
                    : versionStr;
            return Integer.parseInt(main.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 根据风险等级和影响范围生成迁移建议。
     */
    private List<String> buildRecommendations(
            String riskLevel,
            long runningTotal,
            List<Map<String, Object>> stuckInstances,
            List<Map<String, Object>> affectedInstances,
            List<Map<String, Object>> removedNodes,
            List<Map<String, Object>> modifiedNodes) {
        List<String> recs = new ArrayList<>();

        switch (riskLevel) {
            case "NONE":
                recs.add("无在途实例，可直接发布新版本");
                recs.add("建议发布后停用老版本，避免新实例继续使用老版本");
                break;
            case "LOW":
                recs.add("存在 " + runningTotal + " 个在途实例，但节点未变更，风险较低");
                recs.add("建议：发布新版本 + 等待在途实例自然完成后停用老版本");
                recs.add("可选：通知发起人主动撤回后重新发起以使用新版本");
                break;
            case "MEDIUM":
                if (!affectedInstances.isEmpty()) {
                    recs.add("存在 " + affectedInstances.size() + " 个节点的在途实例受影响（类型/审批人变更）");
                    recs.add("建议：通知相关审批人确认变更影响，必要时手工干预");
                }
                if (runningTotal > 100) {
                    recs.add("在途实例数量较多（" + runningTotal + "），建议分批次迁移");
                }
                recs.add("建议：发布新版本但保留老版本激活，待在途实例自然消化后再切换");
                break;
            case "HIGH":
                recs.add("【高危】存在 " + stuckInstances.size() + " 个节点的在途实例将卡死");
                for (Map<String, Object> stuck : stuckInstances) {
                    recs.add("  - 节点 " + stuck.get("nodeCode")
                            + "（" + stuck.get("currentNodeName") + "）有 "
                            + stuck.get("instanceCount") + " 个实例无法继续流转");
                }
                recs.add("建议：发布新版本前必须先处理在途实例：");
                recs.add("  1) 对卡死节点的实例手工强制流转到新版本对应节点");
                recs.add("  2) 或通知发起人撤回后重新发起");
                recs.add("  3) 或保留老版本激活直到所有在途实例完成");
                recs.add("禁止：直接停用老版本会导致在途实例永久卡死");
                break;
            default:
                recs.add("未知风险等级: " + riskLevel);
        }
        return recs;
    }
}
