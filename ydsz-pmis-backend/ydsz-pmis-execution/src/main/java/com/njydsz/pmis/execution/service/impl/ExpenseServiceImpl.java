package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.assembler.NameAssembler;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.ExpenseCreateDTO;
import com.njydsz.pmis.execution.engine.BudgetGuard;
import com.njydsz.pmis.execution.entity.ExpenseDO;
import com.njydsz.pmis.execution.enums.ApprovalStatus;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final NameAssembler nameAssembler;
    private final BudgetGuard budgetGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ExpenseCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getExpenseCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "费用单号不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "金额必须为正数");
        }
        if (dto.getEmployeeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "报销人 ID 不能为空");
        }
        if (expenseMapper.selectByCode(dto.getExpenseCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "费用单号已存在: " + dto.getExpenseCode());
        }
        ExpenseDO e = new ExpenseDO();
        BeanUtils.copyProperties(dto, e);
        if (!StringUtils.hasText(e.getStatus())) e.setStatus(ApprovalStatus.DRAFT.getCode());
        if (e.getTenantId() == null) e.setTenantId(1L);
        if (e.getProviderTraceId() == null) e.setProviderTraceId("");
        if (!StringUtils.hasText(e.getEmployeeName())) {
            try {
                String n = nameAssembler.resolveEmployee(e.getEmployeeId());
                if (n != null) e.setEmployeeName(n);
            } catch (Exception ignore) { }
        }

        // 预算强管控：本次报销 + 项目已发生 ≤ 立项预算
        if (e.getInitiationId() != null && e.getAmount() != null && e.getAmount().signum() > 0) {
            budgetGuard.check(e.getInitiationId(), e.getAmount(), "EXPENSE");
        }

        expenseMapper.insert(e);
        log.info("[Expense] 录入费用: code={} amount={}", e.getExpenseCode(), e.getAmount());
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ApprovalDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        ExpenseDO e = getById(dto.getId());
        ApprovalStatus from = ApprovalStatus.fromCode(e.getStatus());
        ApprovalStatus to = ApprovalStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "费用状态不允许迁移: " + (from == null ? "未知" : from.getDesc()) + " → " + to.getDesc());
        }
        expenseMapper.updateStatus(dto.getId(), to.getCode(),
                dto.getApproverId(), dto.getApproverName());
        log.info("[Expense] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExpenseDO e = getById(id);
        ApprovalStatus s = ApprovalStatus.fromCode(e.getStatus());
        if (s == ApprovalStatus.APPROVED || s == ApprovalStatus.PAID) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已批准/已支付费用单不能删除");
        }
        expenseMapper.deleteById(id);
    }

    @Override
    public ExpenseDO getById(Long id) {
        ExpenseDO e = expenseMapper.selectById(id);
        if (e == null) throw new BizException(BizErrorCode.NOT_FOUND, "费用单不存在");
        return e;
    }

    @Override
    public Page<ExpenseDO> page(int page, int size, String keyword, String status,
                                String expenseType, Long employeeId, Long initiationId) {
        Page<ExpenseDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ExpenseDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(ExpenseDO::getExpenseCode, keyword)
                    .or().like(ExpenseDO::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(ExpenseDO::getStatus, status);
        if (StringUtils.hasText(expenseType)) w.eq(ExpenseDO::getExpenseType, expenseType);
        if (employeeId != null) w.eq(ExpenseDO::getEmployeeId, employeeId);
        if (initiationId != null) w.eq(ExpenseDO::getInitiationId, initiationId);
        w.orderByDesc(ExpenseDO::getExpenseDate);
        return expenseMapper.selectPage(p, w);
    }
}
