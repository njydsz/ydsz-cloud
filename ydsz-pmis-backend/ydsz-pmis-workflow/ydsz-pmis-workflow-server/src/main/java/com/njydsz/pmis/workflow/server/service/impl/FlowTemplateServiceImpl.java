package com.njydsz.pmis.workflow.server.service.impl.definition;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.workflow.domain.dto.definition.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowSkipDO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowTemplateDO;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowTemplateMapper;
import com.njydsz.pmis.workflow.server.service.definition.FlowDefinitionService;
import com.njydsz.pmis.workflow.server.service.definition.FlowTemplateService;
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

    /** 流程模板 Mapper，负责 pmis_flow_template 表的增删改查 */
    private final FlowTemplateMapper templateMapper;
    /** 流程定义服务，模板导入时调用 deploy 部署为草稿定义 */
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
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            FlowTemplateDO template = templateMapper.selectByTemplateCode(templateCode);
            if (template == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
            }
            return toDetailMap(template);
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 获取模板详情异常: templateCode={} err={}", templateCode, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_c2642700", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importTemplate(String templateCode, String flowName) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            FlowTemplateDO template = templateMapper.selectByTemplateCode(templateCode);
            if (template == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
            }
            if (!StringUtils.hasText(template.getBpmnXml())) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f407e561", templateCode);
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
            dto.setTenantId(AuthContext.getTenantIdOrDefault("1"));

            // 部署为草稿
            String definitionId = definitionService.deploy(dto);

            // 增加使用次数
            templateMapper.incrementUseCount(templateCode);

            log.info("[FlowTemplate] 模板导入成功: templateCode={} definitionId={} flowName={}",
                    templateCode, definitionId, dto.getFlowName());
            return definitionId;
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 模板导入异常: templateCode={} err={}", templateCode, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR,
                    "error.workflow.msg_ecc1169b", templateCode, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exportAsTemplate(String definitionId, String templateName, String category) {
        try {
            if (definitionId == null) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_375a4677");
            }
            if (!StringUtils.hasText(templateName)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_bbbf759d");
            }

            // 获取流程定义详情
            Map<String, Object> detail = definitionService.getDetail(definitionId);
            if (detail == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_690c83d8", definitionId);
            }

            FlowDefinitionDO definition = (FlowDefinitionDO) detail.get("definition");
            if (definition == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_690c83d8", definitionId);
            }

            // 生成模板编码
            String templateCode = generateTemplateCode(category, templateName);

            // 生成 BPMN XML
            String bpmnXml = generateBpmnXml(detail);

            // 检查是否已存在（最新版本）
            FlowTemplateDO existing = templateMapper.selectByTemplateCode(templateCode);
            if (existing != null) {
                // P2-9: 已存在 → 创建新版本，旧版本统一降级为 is_latest=0
                templateMapper.markAsNotLatest(templateCode);
                Integer maxVersion = templateMapper.selectMaxVersion(templateCode);
                int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;

                FlowTemplateDO template = new FlowTemplateDO();
                template.setTemplateCode(templateCode);
                template.setTemplateName(templateName);
                template.setCategory(StringUtils.hasText(category) ? category : "GENERAL");
                template.setDescription(definition.getDescription());
                template.setBpmnXml(bpmnXml);
                template.setFormPath(definition.getFormPath());
                template.setUseCount(0);
                template.setSortOrder(existing.getSortOrder() != null ? existing.getSortOrder() : 999);
                // P2-9: 版本化字段 — 沿用 inherit_type 与 parent_template_id 保持继承关系连续
                template.setVersion(newVersion);
                template.setVersionLabel("v" + newVersion + ".0");
                template.setInheritType(existing.getInheritType() != null ? existing.getInheritType() : "STANDALONE");
                template.setParentTemplateId(existing.getParentTemplateId());
                template.setIsLatest(1);
                templateMapper.insert(template);
                log.info("[FlowTemplate] 模板新版本已创建: templateCode={} version={} definitionId={}",
                        templateCode, newVersion, definitionId);
            } else {
                // 新建模板（version=1, is_latest=1, inherit_type=STANDALONE）
                FlowTemplateDO template = new FlowTemplateDO();
                template.setTemplateCode(templateCode);
                template.setTemplateName(templateName);
                template.setCategory(StringUtils.hasText(category) ? category : "GENERAL");
                template.setDescription(definition.getDescription());
                template.setBpmnXml(bpmnXml);
                template.setFormPath(definition.getFormPath());
                template.setUseCount(0);
                template.setSortOrder(999);
                // P2-9: 版本化字段
                template.setVersion(1);
                template.setVersionLabel("v1.0");
                template.setInheritType("STANDALONE");
                template.setIsLatest(1);
                templateMapper.insert(template);
                log.info("[FlowTemplate] 模板已创建: templateCode={} version=1 definitionId={}",
                        templateCode, definitionId);
            }
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 导出模板异常: definitionId={} err={}", definitionId, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_d119b2ed", e.getMessage());
        }
    }

    // ============================== P2-9: 模板继承与版本化 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplateVersions(String templateCode) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            List<FlowTemplateDO> versions = templateMapper.selectVersionsByTemplateCode(templateCode);
            return versions.stream().map(this::toSummaryMap).collect(Collectors.toList());
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 列出版本异常: templateCode={} err={}", templateCode, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTemplateVersion(String templateCode, Integer version) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            // version 为空 → 返回最新版本（保持与 getTemplate 一致的语义）
            if (version == null) {
                FlowTemplateDO latest = templateMapper.selectByTemplateCode(templateCode);
                if (latest == null) {
                    throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
                }
                return toDetailMap(latest);
            }
            if (version < 1) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_a9b0c1d3", version);
            }
            // 在所有版本中筛选指定版本
            List<FlowTemplateDO> versions = templateMapper.selectVersionsByTemplateCode(templateCode);
            if (versions.isEmpty()) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
            }
            return versions.stream()
                    .filter(v -> version.equals(v.getVersion()))
                    .findFirst()
                    .map(this::toDetailMap)
                    .orElseThrow(() -> new SysException(StandardResultCode.NOT_FOUND,
                            "error.workflow.msg_f4a5b6c8", templateCode, version));
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 获取模板版本异常: templateCode={} version={} err={}",
                    templateCode, version, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_c2642700", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer createNewVersion(String templateCode, String versionLabel) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            // 读取当前最新版本作为复制源
            FlowTemplateDO source = templateMapper.selectByTemplateCode(templateCode);
            if (source == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c16cb047", templateCode);
            }
            if (!StringUtils.hasText(source.getBpmnXml())) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f407e561", templateCode);
            }
            // 旧版本统一降级
            templateMapper.markAsNotLatest(templateCode);
            Integer maxVersion = templateMapper.selectMaxVersion(templateCode);
            int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;

            FlowTemplateDO newVer = new FlowTemplateDO();
            newVer.setTemplateCode(templateCode);
            newVer.setTemplateName(source.getTemplateName());
            newVer.setCategory(source.getCategory());
            newVer.setDescription(source.getDescription());
            newVer.setIcon(source.getIcon());
            newVer.setBpmnXml(source.getBpmnXml());
            newVer.setFormPath(source.getFormPath());
            newVer.setUseCount(0);
            newVer.setSortOrder(source.getSortOrder());
            // 沿用继承关系
            newVer.setParentTemplateId(source.getParentTemplateId());
            newVer.setVersion(newVersion);
            newVer.setVersionLabel(StringUtils.hasText(versionLabel) ? versionLabel : "v" + newVersion + ".0");
            newVer.setInheritType(source.getInheritType() != null ? source.getInheritType() : "STANDALONE");
            newVer.setIsLatest(1);
            templateMapper.insert(newVer);

            log.info("[FlowTemplate] 新版本已创建: templateCode={} oldVersion={} newVersion={} label={}",
                    templateCode, source.getVersion(), newVersion, newVer.getVersionLabel());
            return newVersion;
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 创建新版本异常: templateCode={} err={}",
                    templateCode, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_c2642700", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cloneTemplate(String sourceTemplateCode, String newTemplateCode,
                                String newTemplateName, String newCategory) {
        return doCloneOrInherit(sourceTemplateCode, newTemplateCode, newTemplateName, newCategory, "CLONE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String inheritFromParent(String parentTemplateCode, String newTemplateCode,
                                    String newTemplateName, String newCategory) {
        return doCloneOrInherit(parentTemplateCode, newTemplateCode, newTemplateName, newCategory, "INHERIT");
    }

    /**
     * P2-9: cloneTemplate / inheritFromParent 共用复制逻辑。
     *
     * <p>差异仅在 {@code inheritType}：CLONE=克隆独立演进，INHERIT=继承保留父子关联。
     *
     * @param sourceTemplateCode 源/父模板编码（取其最新版本）
     * @param newTemplateCode    新模板编码（必须不存在）
     * @param newTemplateName    新模板名称
     * @param newCategory        新模板分类（可空，默认沿用源模板分类）
     * @param inheritType        CLONE 或 INHERIT
     * @return 新模板编码
     */
    private String doCloneOrInherit(String sourceTemplateCode, String newTemplateCode,
                                    String newTemplateName, String newCategory, String inheritType) {
        try {
            if (!StringUtils.hasText(sourceTemplateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            if (!StringUtils.hasText(newTemplateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_d2e3f4a6");
            }
            if (!StringUtils.hasText(newTemplateName)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_b6c7d8e0");
            }
            // 读取源模板最新版本
            FlowTemplateDO source = templateMapper.selectByTemplateCode(sourceTemplateCode);
            if (source == null) {
                throw new SysException(StandardResultCode.NOT_FOUND,
                        "error.workflow.msg_e3f4a5b7", sourceTemplateCode);
            }
            // 校验新编码未占用
            FlowTemplateDO existing = templateMapper.selectByTemplateCode(newTemplateCode);
            if (existing != null) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.workflow.msg_c1d2e3f5", newTemplateCode);
            }
            // 复制为新模板（version=1, is_latest=1, inherit_type=CLONE/INHERIT, parent_template_id=源模板 id）
            FlowTemplateDO newTemplate = new FlowTemplateDO();
            newTemplate.setTemplateCode(newTemplateCode);
            newTemplate.setTemplateName(newTemplateName);
            newTemplate.setCategory(StringUtils.hasText(newCategory) ? newCategory : source.getCategory());
            newTemplate.setDescription(source.getDescription());
            newTemplate.setIcon(source.getIcon());
            newTemplate.setBpmnXml(source.getBpmnXml());
            newTemplate.setFormPath(source.getFormPath());
            newTemplate.setUseCount(0);
            newTemplate.setSortOrder(source.getSortOrder() != null ? source.getSortOrder() : 999);
            // P2-9: 继承关系字段
            newTemplate.setParentTemplateId(source.getId());
            newTemplate.setVersion(1);
            newTemplate.setVersionLabel("v1.0");
            newTemplate.setInheritType(inheritType);
            newTemplate.setIsLatest(1);
            templateMapper.insert(newTemplate);

            log.info("[FlowTemplate] 模板{}成功: source={} newCode={} parentId={} inheritType={}",
                    "CLONE".equals(inheritType) ? "克隆" : "继承",
                    sourceTemplateCode, newTemplateCode, source.getId(), inheritType);
            return newTemplateCode;
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 复制模板异常: source={} newCode={} inheritType={} err={}",
                    sourceTemplateCode, newTemplateCode, inheritType, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_c2642700", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInheritedTemplates(String parentTemplateCode) {
        try {
            if (!StringUtils.hasText(parentTemplateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            // 先查出父模板主键 ID
            FlowTemplateDO parent = templateMapper.selectByTemplateCode(parentTemplateCode);
            if (parent == null) {
                throw new SysException(StandardResultCode.NOT_FOUND,
                        "error.workflow.msg_b0c1d2e4", parentTemplateCode);
            }
            List<FlowTemplateDO> children = templateMapper.selectByParentTemplateId(parent.getId());
            return children.stream().map(this::toSummaryMap).collect(Collectors.toList());
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 列出继承子模板异常: parentTemplateCode={} err={}",
                    parentTemplateCode, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer syncFromParent(String childTemplateCode) {
        try {
            if (!StringUtils.hasText(childTemplateCode)) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_f68a3fa3");
            }
            // 读取子模板最新版本
            FlowTemplateDO child = templateMapper.selectByTemplateCode(childTemplateCode);
            if (child == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c16cb047", childTemplateCode);
            }
            // 仅 INHERIT 类型可同步
            if (!"INHERIT".equals(child.getInheritType())) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.workflow.msg_d5e6f7a9", childTemplateCode,
                        child.getInheritType() != null ? child.getInheritType() : "null");
            }
            // 读取父模板
            String parentId = child.getParentTemplateId();
            if (!StringUtils.hasText(parentId)) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.workflow.msg_e6f7a8b0", childTemplateCode);
            }
            FlowTemplateDO parent = templateMapper.selectById(parentId);
            if (parent == null) {
                throw new SysException(StandardResultCode.NOT_FOUND,
                        "error.workflow.msg_f7a8b9c1", parentId);
            }
            if (!StringUtils.hasText(parent.getBpmnXml())) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.workflow.msg_f407e561", parent.getTemplateCode());
            }
            // 旧版本降级
            templateMapper.markAsNotLatest(childTemplateCode);
            Integer maxVersion = templateMapper.selectMaxVersion(childTemplateCode);
            int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;

            // 以父模板内容创建子模板新版本，保留子模板自身编码/名称/分类/排序
            FlowTemplateDO newVer = new FlowTemplateDO();
            newVer.setTemplateCode(child.getTemplateCode());
            newVer.setTemplateName(child.getTemplateName());
            newVer.setCategory(child.getCategory());
            newVer.setDescription(parent.getDescription());
            newVer.setIcon(parent.getIcon());
            newVer.setBpmnXml(parent.getBpmnXml());
            newVer.setFormPath(parent.getFormPath());
            newVer.setUseCount(0);
            newVer.setSortOrder(child.getSortOrder());
            // 保持继承关系
            newVer.setParentTemplateId(parentId);
            newVer.setVersion(newVersion);
            newVer.setVersionLabel("v" + newVersion + ".0-synced");
            newVer.setInheritType("INHERIT");
            newVer.setIsLatest(1);
            templateMapper.insert(newVer);

            log.info("[FlowTemplate] 子模板同步父模板成功: childCode={} parentCode={} newVersion={} parentId={}",
                    childTemplateCode, parent.getTemplateCode(), newVersion, parentId);
            return newVersion;
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 同步父模板异常: childCode={} err={}",
                    childTemplateCode, e.getMessage(), e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_c2642700", e.getMessage());
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 将 DO 转为摘要 Map（不含 BPMN XML）
     *
     * <p>P2-9: 包含版本与继承元信息。
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
        // P2-9: 版本与继承元信息
        map.put("parentTemplateId", t.getParentTemplateId());
        map.put("version", t.getVersion());
        map.put("versionLabel", t.getVersionLabel());
        map.put("inheritType", t.getInheritType());
        map.put("isLatest", t.getIsLatest());
        return map;
    }

    /**
     * 将 DO 转为详情 Map（含 BPMN XML）
     *
     * <p>P2-9: 包含版本与继承元信息。
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
        // P2-9: 版本与继承元信息
        map.put("parentTemplateId", t.getParentTemplateId());
        map.put("version", t.getVersion());
        map.put("versionLabel", t.getVersionLabel());
        map.put("inheritType", t.getInheritType());
        map.put("isLatest", t.getIsLatest());
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