package com.njydsz.pmis.project.service.impl.execution;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.execution.TimeEntryApprovalDTO;
import com.njydsz.pmis.project.dto.execution.TimeEntryCreateDTO;
import com.njydsz.pmis.project.engine.TimeEntryValidator;
import com.njydsz.pmis.project.entity.resource.RateCardDO;
import com.njydsz.pmis.project.entity.execution.TimeEntryDO;
import com.njydsz.pmis.project.enums.execution.TimeEntryStatus;
import com.njydsz.pmis.project.mapper.execution.TimeEntryMapper;
import com.njydsz.pmis.project.service.execution.CostAllocationService;
import com.njydsz.pmis.project.service.resource.RateCardService;
import com.njydsz.pmis.project.service.execution.TimeEntryService;
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
import java.util.LinkedHashMap;
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
    private final RateCardService rateCardService;

    /** 旧数据无 rate 时的兜底费率（元/人天），向后兼容 */
    private static final BigDecimal DEFAULT_FALLBACK_RATE = new BigDecimal("800");

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(TimeEntryCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (dto.getEntryDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_f4a1a58d");
        }
        if (dto.getEmployeeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_03f5ae35");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        TimeEntryDO e = new TimeEntryDO();
        BeanUtils.copyProperties(dto, e);
        if (!StringUtils.hasText(e.getLevelCode())) e.setLevelCode("L5");
        if (!StringUtils.hasText(e.getWorkType())) e.setWorkType("REGULAR");
        if (e.getOvertime() == null) e.setOvertime(BigDecimal.ZERO);
        if (!StringUtils.hasText(e.getStatus())) e.setStatus(TimeEntryStatus.DRAFT.getCode());
        if (e.getTenantId() == null) e.setTenantId(TenantContext.getTenantId());
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
            } catch (Exception ex) { log.warn("解析员工名称失败 employeeId={}: {}", e.getEmployeeId(), ex.getMessage(), ex); }
        }

        // 费率卡匹配：用户未指定 rateId 时，按职级 + 填报日期自动命中费率卡
        if (e.getRateId() == null) {
            try {
                RateCardDO card = rateCardService.matchEffective(
                        e.getLevelCode(), null, null, e.getEntryDate());
                if (card != null) {
                    e.setRateId(card.getId());
                    e.setRate(card.getRateAmount());
                } else {
                    log.warn("[TimeEntry] 未匹配到费率卡 levelCode={} entryDate={}，rate 留空",
                            e.getLevelCode(), e.getEntryDate());
                }
            } catch (Exception ex) {
                log.warn("[TimeEntry] 费率卡匹配异常 levelCode={} entryDate={}: {}",
                        e.getLevelCode(), e.getEntryDate(), ex.getMessage());
            }
        }

        timeEntryMapper.insert(e);
        log.info("[TimeEntry] 录入工时: id={} employeeId={} hours={} rateId={} rate={}",
                e.getId(), e.getEmployeeId(), e.getHours(), e.getRateId(), e.getRate());
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String id) {
        TimeEntryDO e = getById(id);
        TimeEntryStatus from = TimeEntryStatus.fromCode(e.getStatus());
        if (from == null || !from.canTransitTo(TimeEntryStatus.SUBMITTED)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_7b9adbb0", (from == null ? "未知" : from.getDesc()));
        }
        timeEntryMapper.updateStatus(id, TimeEntryStatus.SUBMITTED.getCode(), null, null, null);
        log.info("[TimeEntry] 提交工时: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(TimeEntryApprovalDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        TimeEntryDO e = getById(dto.getId());
        TimeEntryStatus from = TimeEntryStatus.fromCode(e.getStatus());
        TimeEntryStatus to = TimeEntryStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_5ad12374", (from == null ? "未知" : from.getDesc()), to.getDesc());
        }
        if (to == TimeEntryStatus.REJECTED && !StringUtils.hasText(dto.getRejectReason())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_4f3bb73f");
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
                // 使用实际命中费率核算成本；旧数据无 rate 时兜底 800 元/人天（向后兼容）
                BigDecimal rate = e.getRate() != null ? e.getRate() : DEFAULT_FALLBACK_RATE;
                if (e.getRate() == null) {
                    log.warn("[TimeEntry] 工时无费率,使用兜底 {} 元/人天 entryId={}", DEFAULT_FALLBACK_RATE, e.getId());
                }
                BigDecimal days = e.getDays() == null
                        ? TimeEntryValidator.toDays(e.getHours())
                        : e.getDays();
                BigDecimal amount = days.multiply(rate);
                costAllocationService.syncFromTimeEntry(
                        e.getId(), e.getInitiationId(), e.getEmployeeId(), e.getEmployeeName(),
                        e.getLevelCode(), period, amount, true);
                log.debug("[TimeEntry] 成本归集成功 entryId={} amount={} rate={}", e.getId(), amount, rate);
            } catch (Exception ex) {
                log.warn("[TimeEntry] 成本归集失败 entryId={}: {}", e.getId(), ex.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        TimeEntryDO e = getById(id);
        if (TimeEntryStatus.fromCode(e.getStatus()) == TimeEntryStatus.APPROVED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_b0ba9ac4");
        }
        timeEntryMapper.deleteById(id);
        log.info("[TimeEntry] 删除工时: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeEntryDO getById(String id) {
        TimeEntryDO e = timeEntryMapper.selectById(id);
        if (e == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_24f2654b");
        return e;
    }

    @Override
    @DataScope(userColumn = "employee_id")
    @Transactional(readOnly = true)
    public Page<TimeEntryDO> page(int page, int size, String keyword, String status,
                                  String employeeId, String initiationId, String taskId,
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
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "employee_id");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(TimeEntryDO::getEntryDate);
        return timeEntryMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryDO> listByEmployeeAndDateRange(String employeeId, LocalDate from, LocalDate to) {
        return timeEntryMapper.selectByEmployeeAndDateRange(employeeId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryDO> listByInitiationAndDateRange(String initiationId, LocalDate from, LocalDate to) {
        return timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateHoursByEmployeeAndLevel(String initiationId,
                                                                      LocalDate from, LocalDate to) {
        return timeEntryMapper.aggregateHoursByEmployeeAndLevel(initiationId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectCrossProject(String employeeId, LocalDate entryDate) {
        if (employeeId == null || entryDate == null) return List.of();
        return timeEntryMapper.detectCrossProject(employeeId, entryDate);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> abnormalStat(String initiationId, String month) {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("initiationId", initiationId);
        stat.put("month", month);
        stat.put("overtimeCount", 0);
        stat.put("missingCount", 0);
        stat.put("abnormalCount", 0);
        stat.put("totalHours", BigDecimal.ZERO);

        if (initiationId == null || initiationId.isBlank()) {
            return stat;
        }

        // 解析月份为日期范围 [月初, 月末]
        String safeMonth = (month == null || month.isBlank())
                ? LocalDate.now().format(PERIOD_FMT) : month;
        LocalDate from;
        LocalDate to;
        try {
            LocalDate firstDay = LocalDate.parse(safeMonth + "-01");
            from = firstDay;
            to = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        } catch (Exception e) {
            log.warn("[abnormalStat] 月份格式非法 month={}, 使用当前月", safeMonth);
            LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
            from = firstDay;
            to = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
            safeMonth = firstDay.format(PERIOD_FMT);
        }
        stat.put("month", safeMonth);

        List<TimeEntryDO> records = timeEntryMapper.selectByInitiationAndDateRange(initiationId, from, to);
        if (records == null || records.isEmpty()) {
            return stat;
        }

        int overtimeCount = 0;
        int missingCount = 0;
        int abnormalCount = 0;
        BigDecimal totalHours = BigDecimal.ZERO;
        for (TimeEntryDO r : records) {
            if (r.getOvertime() != null && r.getOvertime().compareTo(BigDecimal.ZERO) > 0) {
                overtimeCount++;
            }
            if (TimeEntryStatus.DRAFT.getCode().equalsIgnoreCase(r.getStatus())) {
                missingCount++;
            }
            if (TimeEntryStatus.REJECTED.getCode().equalsIgnoreCase(r.getStatus())) {
                abnormalCount++;
            }
            if (r.getHours() != null) {
                totalHours = totalHours.add(r.getHours());
            }
        }

        stat.put("overtimeCount", overtimeCount);
        stat.put("missingCount", missingCount);
        stat.put("abnormalCount", abnormalCount);
        stat.put("totalHours", totalHours);
        return stat;
    }
}
