package com.njydsz.pmis.finance.server.service.finance;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.common.ApprovalDTO;
import com.njydsz.pmis.finance.domain.dto.ExpenseCreateDTO;
import com.njydsz.pmis.finance.domain.entity.ExpenseDO;

/**
 * 费用报销服务
 *
 * <p>提供费用创建、审批状态迁移、查询能力；受预算强管控约束。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ExpenseService {

    /**
     * 创建费用报销
     *
     * @param dto 费用创建参数
     * @return 费用记录ID
     */
    String create(ExpenseCreateDTO dto);

    /**
     * 提交、审批
     *
     * @param dto 审批参数
     */
    void changeStatus(ApprovalDTO dto);

    /**
     * 删除费用记录
     *
     * @param id 费用记录ID
     */
    void delete(String id);

    /**
     * 根据ID查询费用记录
     *
     * @param id 费用记录ID
     * @return 费用实体
     */
    ExpenseDO getById(String id);

    /**
     * 分页查询费用记录
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param expenseType  费用类型
     * @param employeeId   员工ID
     * @param initiationId 项目立项ID
     * @return 分页结果
     */
    Page<ExpenseDO> page(int page, int size, String keyword, String status,
                         String expenseType, String employeeId, String initiationId);
}
