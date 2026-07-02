package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.execution.assembler.NameAssembler;
import com.njydsz.pmis.execution.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.execution.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.execution.engine.TimeEntryValidator;
import com.njydsz.pmis.execution.entity.TimeEntryDO;
import com.njydsz.pmis.execution.enums.TimeEntryStatus;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import com.njydsz.pmis.execution.service.CostAllocationService;
import com.njydsz.pmis.execution.service.TimeEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 工时服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeEntryServiceImpl implements TimeEntryService {

    private final TimeEntryMapper timeEntryMapper;
    private final NameAssembler nameAssembler;
    private final CostAllocationService costAllocationService;

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TimeEntryCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (dto.getEntryDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "工时日期不能为空");
        }
        if (dto.getEmployeeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "员工 ID 不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        TimeEntryDO e = new TimeEntryDO();
        BeanUtils.copyProperties(dto, e);
        if (!StringUtils.hasText(e.getLevelCode())) e.setLevelCode("L5");
        if (!StringUtils.hasText(e.getWorkType())) e.setWorkType("REGULAR");
        if (e.getOvertime() == null) e.setOvertime(BigDecimal.ZERO);
        if (!StringUtils.hasText(e.getStatus())) e.setStatus(TimeEntryStatus.DRAFT.getCode());
        if (e.getTenantId() == null) e.setTenantId(1L);
        if (e.getProviderTraceId() == null) e.setProviderTraceId("");

        // 计算人天
        e.setDays(TimeEntryValidator.toDays(e.getHours()));

        // 校验
        TimeEntryValidator.ValidationResult vr = TimeEntryValidator.validate(e);
        if (!vr.ok) {
            throw new BizException(BizErrorCode.BAD_REQUEST, vr.message);
        }

        // 装配员工名称
        if (!StringUtils.hasText(e.getEmployeeName())) {
            try {
                String n = nameAssembler.resolveEmployee(e.getEmployeeId());
                if (n != null) e.setEmployeeName(n);
            } catch (Exception ignore) { }
        }

        timeEntryMapper.insert(e);
        log.info("[TimeEntry] 录入工时: id={} employeeId={} hours={}",
                e.getId(), e.getEmployeeId(), e.getHours());
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        TimeEntryDO e = getById(id);
        TimeEntryStatus from = TimeEntryStatus.fromCode(e.getStatus());
        if (from == null || !from.canTransitTo(TimeEntryStatus.SUBMITTED)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "工时不允许提交: 当前状态 " + (from == null ? "未知" : from.getDesc()));
        }
        timeEntryMapper.updateStatus(id, TimeEntryStatus.SUBMITTED.getCode(), null, null, null);
        log.info("[TimeEntry] 提交工时: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(TimeEntryApprovalDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        TimeEntryDO e = getById(dto.getId());
        TimeEntryStatus from = TimeEntryStatus.fromCode(e.getStatus());
        TimeEntryStatus to = TimeEntryStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "工时状态不允许迁移: " + (from == null ? "未知" : from.getDesc())
                            + " → " + to.getDesc());
        }
        if (to == TimeEntryStatus.REJECTED && !StringUtils.hasText(dto.getRejectReason())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "驳回原因不能为空");
        }
        timeEntryMapper.updateStatus(dto.getId(), to.getCode(),
                dto.getApproverId(), dto.getApproverName(), dto.getRejectReason());
        e.setApprovedAt(LocalDateTime.now());
        timeEntryMapper.updateById(e);
        log.info("[TimeEntry] 审批工时: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());

        // 审批通过后自动归集人力成本
        if (to == TimeEntryStatus.APPROVED && e.getHours() != null && e.getHours().signum() > 0) {
            try {
                String period = e.getEntryDate() == null
                        ? LocalDate.now().format(PERIOD_FMT)
                        : e.getEntryDate().format(PERIOD_FMT);
                // 简化：人力成本按 8h 折算 1 人天，费率取默认 800 元（人天）
                BigDecimal amount = e.getDays() == null
                        ? TimeEntryValidator.toDays(e.getHours()).multiply(new BigDecimal("800"))
                        : e.getDays().multiply(new BigDecimal("800"));
                costAllocationService.syncFromTimeEntry(
                        e.getId(), e.getInitiationId(), e.getEmployeeId(), e.getEmployeeName(),
                        e.getLevelCode(), period, amount, true);
                log.debug("[TimeEntry] 成本归集成功 entryId={} amount={}", e.getId(), amount);
            } catch (Exception ex) {
                log.warn("[TimeEntry] 成本归集失败 entryId={}: {}", e.getId(), ex.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TimeEntryDO e = getById(id);
        if (TimeEntryStatus.fromCode(e.getStatus()) == TimeEntryStatus.APPROVED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已批准工时不能删除");
        }
        timeEntryMapper.deleteById(id);
        log.info("[TimeEntry] 删除工时: id={}", id);
    }

    @Override
    public TimeEntryDO getById(Long id) {
        TimeEntryDO e = timeEntryMapper.selectById(id);
        if (e == null) throw new BizException(BizErrorCode.NOT_FOUND, "工时不存在");
        return e;
    }

    @Override
    @DataScope(userColumn = "employee_id")
    public Page<TimeEntryDO> page(int page, int size, String keyword, String status,
                                  Long employeeId, Long initiationId, Long taskId,
                                  LocalDate from, LocalDate to) {
        Page<TimeEntryDO> p = new Page<>(page, size);
        LambdaQueryWrapper<TimeEntryDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(TimeEntryDO::getEmployeeName, keyword)
                    .or().like(TimeEntryDO::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(TimeEntryDO::getStatus, status);
        if (employeeId != null) w.eq(TimeEntryDO::getEmployeeId, employeeId);
        if (initiationId != null) w.eq(TimeEntryDO::getInitiationId, initiationId);
        if (taskId != null) w.eq(TimeEntryDO::getTaskId, taskId);
        if (from != null) w.ge(TimeEntryDO::getEntryDate, from);
        if (to != null) w.le(TimeEntryDO::getEntryDate, to);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(TimeEntryDO::getEntryDate);
        return timeEntryMapper.selectPage(p, w);
    }

    @Override
    public List<TimeEntryDO> listByEmployeeAndDateRange(Long employeeId, LocalDate from, LocalDate to) {
        return timeEntryMapper.selectByEmployeeAndDateRange(employeeId, from, to);
    }

    @Override
    public List<TimeEntryDO> listByInitiationAndDateRange(Long initiationId, LocalDate from, LocalDate to) {
        return timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to);
    }

    @Override
    public List<Map<String, Object>> aggregateHoursByEmployeeAndLevel(Long initiationId,
                                                                      LocalDate from, LocalDate to) {
        return timeEntryMapper.aggregateHoursByEmployeeAndLevel(initiationId, from, to);
    }

    @Override
    public List<Map<String, Object>> detectCrossProject(Long employeeId, LocalDate entryDate) {
        if (employeeId == null || entryDate == null) return List.of();
        return timeEntryMapper.detectCrossProject(employeeId, entryDate);
    }
}
