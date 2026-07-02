package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.engine.BpmnModel;
import com.njydsz.pmis.workflow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.engine.JsonHelper;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.enums.FlowSkipType;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

    private final FlowDefinitionMapper definitionMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowSkipMapper skipMapper;
    private final BpmnXmlParser bpmnXmlParser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long deploy(FlowDeployProcessDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFlowCode())
                || !StringUtils.hasText(dto.getFlowName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "flowCode/flowName 不能为空");
        }

        String version = StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0";
        // P2-16: 多租户上下文 - DTO 显式传入优先，否则从 SecurityContext 获取，最后兜底 1L
        Long tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : SecurityContext.getTenantIdOrDefault(1L);

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

        // 3. 写入定义
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setFlowCode(dto.getFlowCode());
        def.setFlowName(dto.getFlowName());
        def.setCategory(dto.getCategory());
        def.setVersion(version);
        def.setModelValue("CLASSICS");
        def.setFormCustom("N");
        def.setFormPath(dto.getFormPath());
        def.setActivityStatus(1);
        def.setIsPublish(0);
        def.setDescription(dto.getDescription());
        def.setTenantId(tenantId);
        def.setProviderTraceId(dto.getProviderTraceId());
        definitionMapper.insert(def);
        Long definitionId = def.getId();

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
        return definitionId;
    }

    @Override
    public void publish(Long definitionId) {
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        definitionMapper.publish(definitionId, 1);
        log.info("[Flow] 发布流程: defId={}", definitionId);
    }

    @Override
    public void deprecate(Long definitionId) {
        definitionMapper.publish(definitionId, 9);
        log.info("[Flow] 停用流程: defId={}", definitionId);
    }

    @Override
    public FlowDefinitionDO getPublished(String flowCode, String version, Long tenantId) {
        if (!StringUtils.hasText(version)) {
            version = "1.0";
        }
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return definitionMapper.selectPublished(flowCode, version, tid);
    }

    @Override
    public FlowDefinitionDO getLatestByCode(String flowCode, Long tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return definitionMapper.selectLatestByCode(flowCode, tid);
    }

    @Override
    public List<FlowDefinitionDO> page(int pageNo, int pageSize, String category, String flowCode) {
        Page<FlowDefinitionDO> page = new Page<>(pageNo, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowDefinitionDO> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(category), FlowDefinitionDO::getCategory, category)
                .like(StringUtils.hasText(flowCode), FlowDefinitionDO::getFlowCode, flowCode)
                .eq(FlowDefinitionDO::getActivityStatus, 1)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt);
        return definitionMapper.selectPage(page, w).getRecords();
    }

    @Override
    public Map<String, Object> getDetail(Long definitionId) {
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
    public void switchActiveVersion(String flowCode, Long definitionId, Long tenantId) {
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
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        // 失效同 flowCode 的其他已发布版本
        definitionMapper.deactivateByFlowCode(flowCode, definitionId, tid);
        // 激活目标版本
        definitionMapper.publish(definitionId, 1);
        log.info("[Flow] 切换流程定义版本: flowCode={} → defId={} tenantId={}",
                flowCode, definitionId, tid);
    }

    // ============================== P2-28: 启用/停用 ==============================

    @Override
    public void enable(Long definitionId) {
        definitionMapper.updateActivityStatus(definitionId, 1);
        log.info("[Flow] 启用流程定义: defId={}", definitionId);
    }

    @Override
    public void disable(Long definitionId) {
        definitionMapper.updateActivityStatus(definitionId, 0);
        log.info("[Flow] 停用流程定义: defId={}", definitionId);
    }

    // ============================== P2-40: 节点坐标更新 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNodeCoordinate(Long definitionId, String nodeCode, String coordinate) {
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
        log.info("[Flow] 更新节点坐标: defId={} node={} coordinate={}",
                definitionId, nodeCode, coordinate);
    }

    // ============================== P2-41: 流程定义草稿编辑 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDefinition(Long definitionId, FlowDeployProcessDTO dto) {
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

        log.info("[Flow] 编辑流程定义草稿: defId={} flowCode={}", definitionId, def.getFlowCode());
    }

    // ============================== GAP-V2-06: 导入/导出 ==============================

    @Override
    public String exportDefinition(Long definitionId) {
        Map<String, Object> detail = getDetail(definitionId);
        if (detail == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        return JSON.toJSONString(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importDefinition(String json, Long tenantId) {
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
        Long newDefinitionId = deploy(dto);
        log.info("[Flow] 导入流程定义成功: flowCode={} version={} newDefId={}",
                dto.getFlowCode(), dto.getVersion(), newDefinitionId);
        return newDefinitionId;
    }

    // ============================== GAP-V2-01: 设计器数据 API ==============================

    @Override
    public Map<String, Object> getDesignerData(Long definitionId) {
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
                edge.put("source", skip.getFromNodeCode());
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
    public void saveDesignerData(Long definitionId, Map<String, Object> designerData) {
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
                    nodeMapper.updateCoordinate(definitionId, nodeCode, coordStr);
                }
                // 更新节点名称（如前端修改了）
                Object nodeName = nodeData.get("nodeName");
                if (nodeName != null) {
                    FlowNodeDO node = nodeMapper.selectByDefinitionAndCode(definitionId, nodeCode);
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
        log.info("[Flow] 设计器数据已保存: definitionId={} nodes={}",
                definitionId, nodes != null ? nodes.size() : 0);
    }

    // ============================== GAP-V2-02: 表单字段配置 ==============================

    @Override
    public String getFormConfig(Long definitionId, String nodeCode) {
        FlowNodeDO node = nodeMapper.selectByDefinitionAndCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        return node.getFormFieldsConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFormConfig(Long definitionId, String nodeCode, String formFieldsConfig) {
        FlowNodeDO node = nodeMapper.selectByDefinitionAndCode(definitionId, nodeCode);
        if (node == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode);
        }
        node.setFormFieldsConfig(formFieldsConfig);
        nodeMapper.updateById(node);
        log.info("[Flow] 表单字段配置已保存: definitionId={} nodeCode={}",
                definitionId, nodeCode);
    }
}
