package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.entity.FlowTemplateDO;
import com.njydsz.pmis.workflow.mapper.FlowTemplateMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程模板市场服务实现
 *
 * <p>基于 pmis_flow_template 数据库表，提供流程模板的查询、导入、导出能力。
 * 导入时通过 {@link FlowDefinitionService#deploy} 将模板的 BPMN XML 部署为草稿流程定义。
 * 导出时将已发布流程定义转换为 BPMN XML 并存入模板表。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTemplateServiceImpl implements FlowTemplateService {

    private final FlowTemplateMapper templateMapper;
    private final FlowDefinitionService definitionService;

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplates(String category) {
        try {
            List<FlowTemplateDO> templates = templateMapper.selectByCategory(category);
            return templates.stream().map(this::toSummaryMap).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[FlowTemplate] 列出模板异常: category={} err={}", category, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTemplate(String templateCode) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            FlowTemplateDO template = templateMapper.selectByTemplateCode(templateCode);
            if (template == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
            }
            return toDetailMap(template);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 获取模板详情异常: templateCode={} err={}", templateCode, e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "error.workflow.msg_c2642700", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importTemplate(String templateCode, String flowName) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            FlowTemplateDO template = templateMapper.selectByTemplateCode(templateCode);
            if (template == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
            }
            if (!StringUtils.hasText(template.getBpmnXml())) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_f407e561", templateCode);
            }

            // 构建部署 DTO，使用 BPMN XML 模式
            FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
            dto.setFlowCode(template.getTemplateCode());
            dto.setFlowName(StringUtils.hasText(flowName) ? flowName : template.getTemplateName());
            dto.setCategory(template.getCategory());
            dto.setDescription(template.getDescription());
            dto.setVersion("1.0");
            dto.setFormPath(template.getFormPath());
            dto.setBpmnXml(template.getBpmnXml());
            dto.setTenantId(SecurityContext.getTenantIdOrDefault("1"));

            // 部署为草稿
            String definitionId = definitionService.deploy(dto);

            // 增加使用次数
            templateMapper.incrementUseCount(templateCode);

            log.info("[FlowTemplate] 模板导入成功: templateCode={} definitionId={} flowName={}",
                    templateCode, definitionId, dto.getFlowName());
            return definitionId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 模板导入异常: templateCode={} err={}", templateCode, e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR,
                    "error.workflow.msg_ecc1169b", templateCode, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exportAsTemplate(String definitionId, String templateName, String category) {
        try {
            if (definitionId == null) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_375a4677");
            }
            if (!StringUtils.hasText(templateName)) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_bbbf759d");
            }

            // 获取流程定义详情
            Map<String, Object> detail = definitionService.getDetail(definitionId);
            if (detail == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_690c83d8", definitionId);
            }

            FlowDefinitionDO definition = (FlowDefinitionDO) detail.get("definition");
            if (definition == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_690c83d8", definitionId);
            }

            // 生成模板编码
            String templateCode = generateTemplateCode(category, templateName);

            // 生成 BPMN XML
            String bpmnXml = generateBpmnXml(detail);

            // 检查是否已存在
            FlowTemplateDO existing = templateMapper.selectByTemplateCode(templateCode);
            if (existing != null) {
                // 更新已有模板
                FlowTemplateDO update = new FlowTemplateDO();
                update.setId(existing.getId());
                update.setTemplateName(templateName);
                update.setCategory(StringUtils.hasText(category) ? category : "GENERAL");
                update.setDescription(definition.getDescription());
                update.setBpmnXml(bpmnXml);
                update.setFormPath(definition.getFormPath());
                templateMapper.updateById(update);
                log.info("[FlowTemplate] 模板已更新: templateCode={} definitionId={}", templateCode, definitionId);
            } else {
                // 新建模板
                FlowTemplateDO template = new FlowTemplateDO();
                template.setTemplateCode(templateCode);
                template.setTemplateName(templateName);
                template.setCategory(StringUtils.hasText(category) ? category : "GENERAL");
                template.setDescription(definition.getDescription());
                template.setBpmnXml(bpmnXml);
                template.setFormPath(definition.getFormPath());
                template.setUseCount(0);
                template.setSortOrder(999);
                templateMapper.insert(template);
                log.info("[FlowTemplate] 模板已创建: templateCode={} definitionId={}", templateCode, definitionId);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 导出模板异常: definitionId={} err={}", definitionId, e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "error.workflow.msg_d119b2ed", e.getMessage());
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 将 DO 转为摘要 Map（不含 BPMN XML）
     */
    private Map<String, Object> toSummaryMap(FlowTemplateDO t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateCode", t.getTemplateCode());
        map.put("templateName", t.getTemplateName());
        map.put("category", t.getCategory());
        map.put("description", t.getDescription());
        map.put("icon", t.getIcon());
        map.put("formPath", t.getFormPath());
        map.put("useCount", t.getUseCount());
        map.put("sortOrder", t.getSortOrder());
        return map;
    }

    /**
     * 将 DO 转为详情 Map（含 BPMN XML）
     */
    private Map<String, Object> toDetailMap(FlowTemplateDO t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateCode", t.getTemplateCode());
        map.put("templateName", t.getTemplateName());
        map.put("category", t.getCategory());
        map.put("description", t.getDescription());
        map.put("icon", t.getIcon());
        map.put("formPath", t.getFormPath());
        map.put("useCount", t.getUseCount());
        map.put("sortOrder", t.getSortOrder());
        map.put("bpmnXml", t.getBpmnXml());
        return map;
    }

    /**
     * 生成模板编码
     */
    private String generateTemplateCode(String category, String templateName) {
        String prefix = StringUtils.hasText(category)
                ? category.toLowerCase().replace(" ", "_")
                : "general";
        String suffix = templateName.toLowerCase()
                .replaceAll("[\\s\\p{Punct}]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (suffix.length() > 30) {
            suffix = suffix.substring(0, 30);
        }
        return prefix + "_" + suffix;
    }

    /**
     * 根据流程定义详情生成 BPMN 2.0 XML
     */
    @SuppressWarnings("unchecked")
    private String generateBpmnXml(Map<String, Object> detail) {
        FlowDefinitionDO definition = (FlowDefinitionDO) detail.get("definition");
        List<FlowNodeDO> nodes = (List<FlowNodeDO>) detail.get("nodes");
        List<FlowSkipDO> skips = (List<FlowSkipDO>) detail.get("skips");

        String processId = definition.getFlowCode();
        String processName = definition.getFlowName();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        xml.append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n");
        xml.append("             targetNamespace=\"http://pmis.ydsz/flow\">\n");
        xml.append("  <process id=\"").append(escapeXml(processId))
                .append("\" name=\"").append(escapeXml(processName))
                .append("\" isExecutable=\"true\">\n");

        // 输出节点
        if (nodes != null) {
            for (FlowNodeDO node : nodes) {
                String nodeCode = node.getNodeCode();
                String nodeName = node.getNodeName();
                Integer nodeType = node.getNodeType();
                String permissionFlag = node.getPermissionFlag();

                String elementName = mapNodeTypeToElement(nodeType);
                xml.append("    <").append(elementName)
                        .append(" id=\"").append(escapeXml(nodeCode)).append("\"");
                if (nodeName != null && !nodeName.isBlank()) {
                    xml.append(" name=\"").append(escapeXml(nodeName)).append("\"");
                }
                if (permissionFlag != null && !permissionFlag.isBlank() && "userTask".equals(elementName)) {
                    xml.append(" flowable:assignee=\"").append(escapeXml(permissionFlag)).append("\"");
                }
                xml.append("/>\n");
            }
        }

        // 输出跳转
        if (skips != null) {
            for (FlowSkipDO skip : skips) {
                // 从 ext JSON 中解析 sourceRef
                String fromNodeCode = extractSourceRef(skip.getExt());
                String toNodeCode = skip.getNextNodeCode();
                String skipName = skip.getSkipName();
                String skipCondition = skip.getSkipCondition();

                String flowId = "flow_" + (fromNodeCode != null ? fromNodeCode : "") + "_to_"
                        + (toNodeCode != null ? toNodeCode : "");

                if (skipCondition != null && !skipCondition.isBlank()) {
                    xml.append("    <sequenceFlow id=\"").append(escapeXml(flowId))
                            .append("\" sourceRef=\"").append(escapeXml(fromNodeCode))
                            .append("\" targetRef=\"").append(escapeXml(toNodeCode)).append("\">\n");
                    xml.append("      <conditionExpression xsi:type=\"tFormalExpression\">")
                            .append(escapeXml(skipCondition))
                            .append("</conditionExpression>\n");
                    xml.append("    </sequenceFlow>\n");
                } else {
                    xml.append("    <sequenceFlow id=\"").append(escapeXml(flowId))
                            .append("\" sourceRef=\"").append(escapeXml(fromNodeCode))
                            .append("\" targetRef=\"").append(escapeXml(toNodeCode)).append("\"");
                    if (skipName != null && !skipName.isBlank()) {
                        xml.append(" name=\"").append(escapeXml(skipName)).append("\"");
                    }
                    xml.append("/>\n");
                }
            }
        }

        xml.append("  </process>\n");
        xml.append("</definitions>\n");
        return xml.toString();
    }

    /**
     * 节点类型码映射为 BPMN 元素名
     */
    private String mapNodeTypeToElement(Integer nodeType) {
        if (nodeType == null) {
            return "userTask";
        }
        return switch (nodeType) {
            case 0 -> "startEvent";
            case 1 -> "userTask";
            case 2 -> "userTask";       // 抄送节点也映射为 userTask
            case 3 -> "exclusiveGateway";
            case 4 -> "parallelGateway";
            case 5 -> "inclusiveGateway";
            case 6 -> "endEvent";
            case 7 -> "callActivity";    // 子流程
            default -> "userTask";
        };
    }

    /**
     * 从 skip 的 ext JSON 中提取 sourceRef（源节点编码）
     */
    private String extractSourceRef(String ext) {
        if (ext == null || ext.isBlank()) {
            return null;
        }
        try {
            JSONObject extJson = JSON.parseObject(ext);
            if (extJson != null) {
                String sourceRef = extJson.getString("sourceRef");
                if (sourceRef != null && !sourceRef.isBlank()) {
                    return sourceRef;
                }
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return null;
    }

    /**
     * XML 转义
     */
    private String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}