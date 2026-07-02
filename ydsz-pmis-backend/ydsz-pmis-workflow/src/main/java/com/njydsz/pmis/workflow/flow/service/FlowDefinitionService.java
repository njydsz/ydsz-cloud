package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.workflow.flow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;

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
}
