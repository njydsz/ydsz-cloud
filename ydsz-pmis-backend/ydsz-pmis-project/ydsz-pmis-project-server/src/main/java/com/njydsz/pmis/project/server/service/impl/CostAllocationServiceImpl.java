package com.njydsz.pmis.project.server.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.project.domain.entity.CostAllocationDO;
import com.njydsz.pmis.project.domain.enums.CostType;
import com.njydsz.pmis.project.infra.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.server.service.CostAllocationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 成本分摊服务实现
 *
 * <p>负责将工时、采购、费用等源头数据同步为统一的成本分摊记录，
 * 支持按期间、项目、成本类型多维查询与聚合。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostAllocationServiceImpl implements CostAllocationService {

    /** 成本分摊 Mapper */
    private final CostAllocationMapper costAllocationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String syncFromTimeEntry(String timeEntryId, String initiationId, String employeeId,
                                   String employeeName, String levelCode,
                                   String period, BigDecimal amount, boolean billable) {
        CostAllocationDO c = new CostAllocationDO();
        c.setInitiationId(initiationId);
        c.setPeriod(period);
        c.setCostType(CostType.LABOR.getCode());
        c.setSourceId(timeEntryId);
        c.setSourceType("TIME_ENTRY");
        c.setDescription("工时成本");
        c.setAmount(amount);
        c.setBillable(billable ? 1 : 0);
        c.setAllocated(0);
        c.setEmployeeId(employeeId);
        c.setEmployeeName(employeeName);
        c.setLevelCode(levelCode);
        c.setTenantId(TenantContext.getTenantId());
        c.setProviderTraceId("");
        costAllocationMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String syncFromPurchase(String purchaseId, String initiationId, String period,
                                  BigDecimal amount, boolean billable) {
        CostAllocationDO c = new CostAllocationDO();
        c.setInitiationId(initiationId);
        c.setPeriod(period);
        c.setCostType(CostType.PURCHASE.getCode());
        c.setSourceId(purchaseId);
        c.setSourceType("PURCHASE");
        c.setDescription("采购成本");
        c.setAmount(amount);
        c.setBillable(billable ? 1 : 0);
        c.setTenantId(TenantContext.getTenantId());
        c.setProviderTraceId("");
        costAllocationMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String syncFromExpense(String expenseId, String initiationId, String period,
                                 BigDecimal amount, boolean billable) {
        CostAllocationDO c = new CostAllocationDO();
        c.setInitiationId(initiationId);
        c.setPeriod(period);
        c.setCostType(CostType.EXPENSE.getCode());
        c.setSourceId(expenseId);
        c.setSourceType("EXPENSE");
        c.setDescription("费用成本");
        c.setAmount(amount);
        c.setBillable(billable ? 1 : 0);
        c.setTenantId(TenantContext.getTenantId());
        c.setProviderTraceId("");
        costAllocationMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> monthlySummary(String initiationId) {
        if (initiationId == null) return List.of();
        return costAllocationMapper.monthlySummary(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sumByType(String initiationId, String period) {
        if (initiationId == null) return List.of();
        return costAllocationMapper.sumByType(initiationId, period);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CostAllocationDO> listByInitiationAndPeriod(String initiationId, String period) {
        return costAllocationMapper.selectByInitiationAndPeriod(initiationId, period);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllocated(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            CostAllocationDO c = costAllocationMapper.selectById(id);
            if (c != null) {
                c.setAllocated(1);
                costAllocationMapper.updateById(c);
            }
        }
    }
}
