package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;

import java.util.List;
import java.util.Map;

/**
 * 流程定义 Service
 *
 * <p>提供流程部署、发布、停用、查询等能力，是工作流引擎的入口服务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowDefinitionService {

    /**
     * 部署流程（基于 JSON 模型）
     *
     * @return 流程定义 ID
     */
    Long deploy(FlowDeployProcessDTO dto);

    /**
     * 发布流程
     */
    void publish(Long definitionId);

    /**
     * 停用（失效）流程
     */
    void deprecate(Long definitionId);

    /**
     * 查最新已发布版本
     */
    FlowDefinitionDO getPublished(String flowCode, String version, Long tenantId);

    /**
     * 按编码查最新
     */
    FlowDefinitionDO getLatestByCode(String flowCode, Long tenantId);

    /**
     * 分页查询
     */
    List<FlowDefinitionDO> page(int pageNo, int pageSize, String category, String flowCode);

    /**
     * P2-21: 流程定义详情查询（含节点 + 跳转）
     *
     * @param definitionId 流程定义 ID
     * @return Map 包含 definition / nodes / skips 三个 key；定义不存在返回 null
     */
    Map<String, Object> getDetail(Long definitionId);

    /**
     * P2-27: 切换流程定义的激活版本 — 失效同 flowCode 其他已发布版本，激活目标版本
     *
     * @param flowCode      流程编码
     * @param definitionId  目标流程定义 ID
     * @param tenantId      租户 ID（可空，默认 1L）
     */
    void switchActiveVersion(String flowCode, Long definitionId, Long tenantId);

    /**
     * P2-28: 启用流程定义（activityStatus = 1）
     *
     * @param definitionId 流程定义 ID
     */
    void enable(Long definitionId);

    /**
     * P2-28: 停用流程定义（activityStatus = 0）
     *
     * @param definitionId 流程定义 ID
     */
    void disable(Long definitionId);

    /**
     * P2-40: 更新节点坐标（供前端设计器保存布局）
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param coordinate   坐标 JSON 字符串（如 {"x":100,"y":200}）
     */
    void updateNodeCoordinate(Long definitionId, String nodeCode, String coordinate);

    /**
     * P2-41: 编辑未发布的流程定义草稿（更新元数据 + 可选更新节点/跳转）
     *
     * @param definitionId 流程定义 ID
     * @param dto          部署参数（含更新后的元数据与节点/跳转）
     */
    void updateDefinition(Long definitionId, FlowDeployProcessDTO dto);

    /**
     * GAP-V2-06: 导出流程定义为 JSON（含定义元数据 + 节点 + 跳转）
     *
     * @param definitionId 流程定义 ID
     * @return JSON 字符串，包含 definition / nodes / skips 三个部分
     */
    String exportDefinition(Long definitionId);

    /**
     * GAP-V2-06: 从 JSON 导入流程定义（创建为草稿）
     *
     * @param json     导出的 JSON 字符串
     * @param tenantId 租户 ID（可空，默认从上下文获取）
     * @return 新创建的流程定义 ID
     */
    Long importDefinition(String json, Long tenantId);

    /**
     * GAP-V2-01: 获取设计器数据 — 返回完整流程图（节点+边+坐标），供前端设计器加载
     *
     * @param definitionId 流程定义 ID
     * @return Map 包含 definition / nodes（含 coordinate）/ edges（含 condition）
     */
    Map<String, Object> getDesignerData(Long definitionId);

    /**
     * GAP-V2-01: 批量保存设计器数据 — 一次性保存节点坐标 + 边 + 节点属性
     *
     * @param definitionId 流程定义 ID
     * @param designerData 设计器数据（nodes + edges + definition 元数据）
     */
    void saveDesignerData(Long definitionId, Map<String, Object> designerData);

    /**
     * GAP-V2-02: 获取节点表单字段配置
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @return formFieldsConfig JSON 字符串（如 {"fieldKey":"EDIT|READONLY|HIDDEN",...}）
     */
    String getFormConfig(Long definitionId, String nodeCode);

    /**
     * GAP-V2-02: 保存节点表单字段配置
     *
     * @param definitionId      流程定义 ID
     * @param nodeCode          节点编码
     * @param formFieldsConfig  字段权限 JSON 字符串
     */
    void saveFormConfig(Long definitionId, String nodeCode, String formFieldsConfig);

    /**
     * P1-2: 获取节点 SLA 配置（JSON 字符串）
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @return slaConfig JSON 字符串（如
     *   {@code {"timeoutMinutes":120,"action":"REMIND","reminderIntervalMinutes":60,"maxReminders":3,"escalateUserId":1}}），
     *   未配置返回 null
     */
    String getSlaConfig(Long definitionId, String nodeCode);

    /**
     * P1-2: 保存节点 SLA 配置
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param slaConfig    SLA 配置 JSON 字符串
     */
    void saveSlaConfig(Long definitionId, String nodeCode, String slaConfig);

    /**
     * 列出流程定义的所有历史版本
     *
     * @param definitionId 流程定义 ID（用于获取 flowCode）
     * @return 版本列表，每项包含 id / version / flowName / isPublish / activityStatus / createdAt / updatedAt
     */
    List<Map<String, Object>> listVersions(Long definitionId);

    /**
     * 对比两个版本的节点和连线差异
     *
     * @param definitionId 流程定义 ID（用于获取 flowCode）
     * @param version1     版本号 1（整数）
     * @param version2     版本号 2（整数）
     * @return Map 包含 version1 / version2 / nodeChanges / skipChanges
     */
    Map<String, Object> diffVersions(Long definitionId, Integer version1, Integer version2);

    /**
     * GAP-P1-6: 从 BPMN 部署包 .zip 批量导入流程定义。
     *
     * <p>对标 Activiti/Flowable 的 `repositoryService.createDeployment().addZipInputStream()` 能力。
     * 遍历 zip 内的 {@code .bpmn} / {@code .bpmn20.xml} 文件，逐个解析并委托 {@link #deploy} 入库。
     * 单个文件失败不影响其他文件（每个 deploy 是独立事务）。
     *
     * @param zipBytes zip 文件字节数组
     * @param tenantId 租户 ID（可空，默认从 SecurityContext 获取）
     * @return 批量导入结果：successCount / failedItems（fileName + reason）
     */
    Map<String, Object> batchDeployFromZip(byte[] zipBytes, Long tenantId);
}
