package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.PurchaseCreateDTO;
import com.njydsz.pmis.execution.entity.PurchaseDO;

/**
 * 采购成本服务
 */
public interface PurchaseService {

    Long create(PurchaseCreateDTO dto);

    /**
     * 提交、审批
     */
    void changeStatus(ApprovalDTO dto);

    void delete(Long id);

    PurchaseDO getById(Long id);

    Page<PurchaseDO> page(int page, int size, String keyword, String status, Long initiationId);
}
