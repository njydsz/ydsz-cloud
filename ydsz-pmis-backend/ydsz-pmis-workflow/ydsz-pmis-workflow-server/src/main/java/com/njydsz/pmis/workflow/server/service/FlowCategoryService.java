package com.njydsz.pmis.workflow.server.service;

import com.njydsz.pmis.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowCategoryDO;

import java.util.List;

/**
 * 流程分类服务接口
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface FlowCategoryService {

    /**
     * 查询全部分类（树形结构，按 sortNum 排序）
     *
     * @param tenantId 租户 ID
     * @return 分类列表（扁平结构，前端自行构建树）
     */
    List<FlowCategoryDO> listAll(String tenantId);

    /**
     * 新增分类
     *
     * @param dto      分类 DTO
     * @param tenantId 租户 ID
     * @return 分类 ID
     */
    String create(FlowCategoryDTO dto, String tenantId);

    /**
     * 编辑分类
     *
     * @param dto 分类 DTO（id 必传）
     */
    void update(FlowCategoryDTO dto);

    /**
     * 删除分类（校验是否有子分类和关联的流程定义）
     *
     * @param id 分类 ID
     */
    void delete(String id);
}
