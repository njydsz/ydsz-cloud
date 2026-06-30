package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.ExpenseCreateDTO;
import com.njydsz.pmis.execution.entity.ExpenseDO;

/**
 * 费用报销服务
 */
public interface ExpenseService {

    Long create(ExpenseCreateDTO dto);

    void changeStatus(ApprovalDTO dto);

    void delete(Long id);

    ExpenseDO getById(Long id);

    Page<ExpenseDO> page(int page, int size, String keyword, String status,
                         String expenseType, Long employeeId, Long initiationId);
}
