package com.njydsz.pmis.sales.server.service.contract;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.sales.domain.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.sales.domain.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.sales.domain.entity.ContractTemplateDO;

import java.util.List;

/**
 * 合同模板服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ContractTemplateService {

    /**
     * 创建合同模板。
     *
     * @param dto 模板创建参数
     * @return 模板 ID
     */
    String create(ContractTemplateCreateDTO dto);

    /**
     * 模板状态迁移（DRAFT/PUBLISHED/DEPRECATED 之间转换）。
     *
     * @param dto 状态迁移参数
     */
    void changeStatus(ContractTemplateStatusDTO dto);

    /**
     * 删除合同模板（逻辑删除）。
     *
     * @param id 模板 ID
     */
    void delete(String id);

    /**
     * 根据模板 ID 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体；不存在返回 null
     */
    ContractTemplateDO getById(String id);

    /**
     * 分页查询合同模板。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（模板编码/名称模糊匹配），可空
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 分页结果
     */
    Page<ContractTemplateDO> page(int page, int size, String keyword,
                                  String contractType, String status);

    /**
     * 按合同类型与状态查询模板列表（供合同创建时下拉选择）。
     *
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 模板列表
     */
    List<ContractTemplateDO> listByType(String contractType, String status);
}
