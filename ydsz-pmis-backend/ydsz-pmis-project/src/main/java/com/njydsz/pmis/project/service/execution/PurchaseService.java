package com.njydsz.pmis.project.service.execution;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.common.ApprovalDTO;
import com.njydsz.pmis.project.dto.execution.PurchaseCreateDTO;
import com.njydsz.pmis.project.entity.execution.PurchaseDO;

/**
 * 采购成本服务
 *
 * <p>提供采购单创建、审批状态迁移、查询能力；受预算强管控约束。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface PurchaseService {

    /**
     * 创建采购单
     *
     * @param dto 采购创建参数
     * @return 采购单ID
     */
    String create(PurchaseCreateDTO dto);

    /**
     * 提交、审批
     *
     * @param dto 审批参数
     */
    void changeStatus(ApprovalDTO dto);

    /**
     * 删除采购单
     *
     * @param id 采购单ID
     */
    void delete(String id);

    /**
     * 根据ID查询采购单
     *
     * @param id 采购单ID
     * @return 采购单实体
     */
    PurchaseDO getById(String id);

    /**
     * 分页查询采购单
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param initiationId 项目立项ID
     * @return 分页结果
     */
    Page<PurchaseDO> page(int page, int size, String keyword, String status, String initiationId);
}
