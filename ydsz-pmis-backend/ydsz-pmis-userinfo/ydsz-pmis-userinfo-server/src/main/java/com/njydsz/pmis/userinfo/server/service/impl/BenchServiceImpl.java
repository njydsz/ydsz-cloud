package com.njydsz.pmis.userinfo.server.service.impl.resource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.userinfo.domain.dto.resource.BenchRecordCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.resource.BenchRecordDO;
import com.njydsz.pmis.userinfo.domain.enums.resource.BenchStatus;
import com.njydsz.pmis.userinfo.infra.mapper.resource.BenchRecordMapper;
import com.njydsz.pmis.userinfo.server.engine.BenchCostCalculator;
import com.njydsz.pmis.userinfo.server.service.resource.BenchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bench 闲置池服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchServiceImpl implements BenchService {

    private final BenchRecordMapper benchMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String act(BenchRecordCreateDTO dto) {
        if (dto == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (!StringUtils.hasText(dto.getBenchCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_b0695d8f");
        }
        if (dto.getEmployeeId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_03f5ae35");
        }
        if (benchMapper.selectByCode(dto.getBenchCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.user.msg_31770192", dto.getBenchCode());
        }
        String action = dto.getAction() == null ? "" : dto.getAction().toUpperCase();
        if ("ENTER".equals(action)) return autoEnter(dto);
        if ("EXIT".equals(action)) {
            autoExit(dto.getEmployeeId(), dto.getSourceAssignment(),
                    dto.getReasonType(), dto.getExitDate() != null ? dto.getExitDate() : LocalDate.now());
            return null;
        }
        throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_f4a32874", dto.getAction());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String autoEnter(BenchRecordCreateDTO dto) {
        // 校验当前没有活跃 Bench
        BenchRecordDO active = benchMapper.selectActiveByEmployee(dto.getEmployeeId());
        if (active != null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_d48cd922", active.getBenchCode());
        }
        BenchRecordDO b = new BenchRecordDO();
        BeanUtils.copyProperties(dto, b);
        b.setBenchReason("ENTER");
        b.setStatus(BenchStatus.ACTIVE.getCode());
        if (b.getBenchDate() == null) b.setBenchDate(LocalDate.now());
        if (b.getDailyCost() == null) b.setDailyCost(BigDecimal.ZERO);
        if (b.getTenantId() == null) b.setTenantId(TenantContext.getTenantId());
        if (b.getProviderTraceId() == null) b.setProviderTraceId("");
        // 计算初始成本
        b.setIdleDays(BenchCostCalculator.idleDays(b.getBenchDate(), b.getExitDate()));
        b.setTotalIdleCost(BenchCostCalculator.totalIdleCost(b.getDailyCost(), b.getIdleDays()));
        benchMapper.insert(b);
        log.info("[Bench] 入池: code={} emp={} reason={}",
                b.getBenchCode(), b.getEmployeeId(), b.getReasonType());
        return b.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoExit(String employeeId, String sourceAssignment, String reasonType, LocalDate exitDate) {
        if (employeeId == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_03f5ae35");
        BenchRecordDO active = benchMapper.selectActiveByEmployee(employeeId);
        if (active == null) {
            log.warn("[Bench] 员工无活跃 Bench 记录，无需出池: emp={}", employeeId);
            return;
        }
        if (exitDate == null) exitDate = LocalDate.now();
        active.setExitDate(exitDate);
        active.setStatus(BenchStatus.EXITED.getCode());
        active.setBenchReason("EXIT");
        if (reasonType != null) active.setReasonType(reasonType);
        if (sourceAssignment != null) active.setSourceAssignment(sourceAssignment);
        active.setIdleDays(BenchCostCalculator.idleDays(active.getBenchDate(), exitDate));
        active.setTotalIdleCost(BenchCostCalculator.totalIdleCost(active.getDailyCost(), active.getIdleDays()));
        benchMapper.updateById(active);
        log.info("[Bench] 出池: code={} emp={} days={} cost={}",
                active.getBenchCode(), employeeId, active.getIdleDays(), active.getTotalIdleCost());
    }

    @Override
    @Transactional(readOnly = true)
    public BenchRecordDO getById(String id) {
        if (id == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.user.msg_411b6827");
        BenchRecordDO b = benchMapper.selectById(id);
        if (b == null) throw new SysException(StandardResultCode.NOT_FOUND, "error.user.msg_e848f489");
        return b;
    }

    @Override
    @Transactional(readOnly = true)
    public BenchRecordDO getActiveByEmployee(String employeeId) {
        if (employeeId == null) return null;
        return benchMapper.selectActiveByEmployee(employeeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByPool() {
        return benchMapper.aggregateByPool(BenchStatus.ACTIVE.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> flowByDateRange(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(30);
        if (to == null) to = LocalDate.now();
        return benchMapper.flowByDateRange(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BenchRecordDO> page(int page, int size, String poolId, String status) {
        Page<BenchRecordDO> p = new Page<>(page, size);
        LambdaQueryWrapper<BenchRecordDO> w = new LambdaQueryWrapper<>();
        if (poolId != null) w.eq(BenchRecordDO::getPoolId, poolId);
        if (StringUtils.hasText(status)) w.eq(BenchRecordDO::getStatus, status);
        w.orderByDesc(BenchRecordDO::getBenchDate);
        return benchMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalIdleCost() {
        List<Map<String, Object>> rows = aggregateByPool();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            Object v = row.get("total_cost");
            if (v instanceof BigDecimal d) total = total.add(d);
            else if (v instanceof Number n) total = total.add(BigDecimal.valueOf(n.doubleValue()));
        }
        return total;
    }

    /** 构造用于 Map 返回的辅助（保留扩展点） */
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new HashMap<>();
        out.put("activePools", aggregateByPool());
        out.put("totalIdleCost", totalIdleCost());
        out.put("recentFlow", flowByDateRange(LocalDate.now().minusDays(7), LocalDate.now()));
        return out;
    }
}