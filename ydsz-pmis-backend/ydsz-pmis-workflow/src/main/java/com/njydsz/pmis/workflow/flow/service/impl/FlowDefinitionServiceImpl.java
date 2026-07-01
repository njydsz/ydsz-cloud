package com.njydsz.pmis.workflow.flow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.flow.engine.BpmnModel;
import com.njydsz.pmis.workflow.flow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowSkipType;
import com.njydsz.pmis.workflow.flow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.flow.service.FlowDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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
        Long tenantId = dto.getTenantId() == null ? 1L : dto.getTenantId();

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
        return definitionMapper.selectPublished(flowCode, version,
                tenantId == null ? 1L : tenantId);
    }

    @Override
    public FlowDefinitionDO getLatestByCode(String flowCode, Long tenantId) {
        return definitionMapper.selectLatestByCode(flowCode,
                tenantId == null ? 1L : tenantId);
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
}
