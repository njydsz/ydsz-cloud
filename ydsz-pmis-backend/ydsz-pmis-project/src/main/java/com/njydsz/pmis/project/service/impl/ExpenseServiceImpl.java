package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.ApprovalDTO;
import com.njydsz.pmis.project.dto.ExpenseCreateDTO;
import com.njydsz.pmis.project.engine.BudgetGuard;
import com.njydsz.pmis.project.entity.ExpenseDO;
import com.njydsz.pmis.project.enums.ApprovalStatus;
import com.njydsz.pmis.project.mapper.ExpenseMapper;
import com.njydsz.pmis.project.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 费用报销服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final NameAssembler nameAssembler;
    private final BudgetGuard budgetGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ExpenseCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getExpenseCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_01247121");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_2b7b69af");
        }
        if (dto.getEmployeeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_9f487f28");
        }
        if (expenseMapper.selectByCode(dto.getExpenseCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.execution.msg_37f1deeb", dto.getExpenseCode());
        }
        ExpenseDO e = new ExpenseDO();
        BeanUtils.copyProperties(dto, e);
        if (!StringUtils.hasText(e.getStatus())) e.setStatus(ApprovalStatus.DRAFT.getCode());
        if (e.getTenantId() == null) e.setTenantId(TenantContext.getTenantId());
        if (e.getProviderTraceId() == null) e.setProviderTraceId("");
        if (!StringUtils.hasText(e.getEmployeeName())) {
            try {
                String n = nameAssembler.resolveEmployee(e.getEmployeeId());
                if (n != null) e.setEmployeeName(n);
            } catch (Exception ex) { log.warn("解析员工名称失败 employeeId={}: {}", e.getEmployeeId(), ex.getMessage(), ex); }
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        ExpenseDO e = getById(dto.getId());
        ApprovalStatus from = ApprovalStatus.fromCode(e.getStatus());
        ApprovalStatus to = ApprovalStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_ba0d6420", (from == null ? "未知" : from.getDesc()), to.getDesc());
        }
        expenseMapper.updateStatus(dto.getId(), to.getCode(),
                dto.getApproverId(), dto.getApproverName());
        log.info("[Expense] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ExpenseDO e = getById(id);
        ApprovalStatus s = ApprovalStatus.fromCode(e.getStatus());
        if (s == ApprovalStatus.APPROVED || s == ApprovalStatus.PAID) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_bf3459b1");
        }
        expenseMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseDO getById(String id) {
        ExpenseDO e = expenseMapper.selectById(id);
        if (e == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_fe55e2d1");
        return e;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseDO> page(int page, int size, String keyword, String status,
                                String expenseType, String employeeId, String initiationId) {
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
