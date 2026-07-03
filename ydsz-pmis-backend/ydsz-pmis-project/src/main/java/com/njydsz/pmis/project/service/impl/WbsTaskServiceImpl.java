package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.project.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.project.entity.WbsTaskDO;
import com.njydsz.pmis.project.enums.WbsTaskStatus;
import com.njydsz.pmis.project.mapper.WbsTaskMapper;
import com.njydsz.pmis.project.service.WbsTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WbsTaskServiceImpl implements WbsTaskService {

    private final WbsTaskMapper wbsTaskMapper;
    private final NameAssembler nameAssembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WbsTaskCreateDTO dto) {
        validate(dto);
        if (wbsTaskMapper.selectByCode(dto.getTaskCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.execution.msg_aecdf567" + dto.getTaskCode());
        }
        WbsTaskDO t = new WbsTaskDO();
        BeanUtils.copyProperties(dto, t);
        if (!StringUtils.hasText(t.getStatus())) t.setStatus(WbsTaskStatus.PLANNED.getCode());
        if (!StringUtils.hasText(t.getPriority())) t.setPriority("NORMAL");
        if (t.getTaskLevel() == null || t.getTaskLevel() < 1) t.setTaskLevel(1);
        if (t.getPlannedEffort() == null) t.setPlannedEffort(BigDecimal.ZERO);
        if (t.getActualEffort() == null) t.setActualEffort(BigDecimal.ZERO);
        if (t.getProgressPct() == null) t.setProgressPct(BigDecimal.ZERO);
        if (t.getMilestone() == null) t.setMilestone(0);
        if (t.getTenantId() == null) t.setTenantId(TenantContext.getTenantId());
        if (t.getProviderTraceId() == null) t.setProviderTraceId("");

        // 计算工期
        if (t.getDurationDays() == null && t.getPlannedStartDate() != null && t.getPlannedEndDate() != null) {
            long days = ChronoUnit.DAYS.between(t.getPlannedStartDate(), t.getPlannedEndDate());
            t.setDurationDays((int) Math.max(0, days));
        }
        // 计算 WBS 路径
        if (t.getParentId() != null && t.getParentId() > 0) {
            WbsTaskDO parent = wbsTaskMapper.selectById(t.getParentId());
            if (parent != null) {
                t.setTaskLevel((parent.getTaskLevel() == null ? 1 : parent.getTaskLevel()) + 1);
                String prefix = parent.getWbsPath() == null ? ("/" + parent.getId()) : parent.getWbsPath();
                t.setWbsPath(prefix);
            }
        }
        // 装配负责人名称
        if (!StringUtils.hasText(t.getOwnerName()) && t.getOwnerId() != null) {
            try {
                String n = nameAssembler.resolveEmployee(t.getOwnerId());
                if (n != null) t.setOwnerName(n);
            } catch (Exception e) { log.warn("解析负责人名称失败 ownerId={}: {}", t.getOwnerId(), e.getMessage(), e); }
        }
        wbsTaskMapper.insert(t);
        log.info("[WbsTask] 创建任务: code={} name={}", t.getTaskCode(), t.getTaskName());
        return t.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(WbsTaskStatusDTO dto) {
        WbsTaskDO t = getById(dto.getId());
        WbsTaskStatus from = WbsTaskStatus.fromCode(t.getStatus());
        WbsTaskStatus to = WbsTaskStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_7bc741c6" + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_2e33226a" + t.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_28f70737" + from.getDesc() + " → " + to.getDesc());
        }
        wbsTaskMapper.updateStatus(t.getId(), to.getCode());
        // 同步进度
        if (dto.getProgressPct() != null) {
            wbsTaskMapper.updateProgress(t.getId(), dto.getProgressPct(), dto.getActualEffort());
        }
        // 启动/完成时填充实际日期
        if (to == WbsTaskStatus.IN_PROGRESS && t.getActualStartDate() == null) {
            t.setActualStartDate(LocalDate.now());
            wbsTaskMapper.updateById(t);
        }
        if (to == WbsTaskStatus.COMPLETED) {
            t.setActualEndDate(LocalDate.now());
            if (dto.getProgressPct() == null) {
                wbsTaskMapper.updateProgress(t.getId(), new BigDecimal("100"), dto.getActualEffort());
            }
            wbsTaskMapper.updateById(t);
        }
        log.info("[WbsTask] 状态迁移: id={} {} -> {}", t.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long id, BigDecimal progressPct, BigDecimal actualEffort) {
        WbsTaskDO t = getById(id);
        if (progressPct != null) {
            if (progressPct.signum() < 0 || progressPct.compareTo(new BigDecimal("100")) > 0) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_627bf88e");
            }
            t.setProgressPct(progressPct);
        }
        if (actualEffort != null) {
            t.setActualEffort(actualEffort);
        }
        wbsTaskMapper.updateById(t);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WbsTaskDO t = getById(id);
        if (WbsTaskStatus.fromCode(t.getStatus()) == WbsTaskStatus.IN_PROGRESS) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_ce5c0a72");
        }
        wbsTaskMapper.deleteById(id);
        log.info("[WbsTask] 删除任务: id={}", id);
    }

    @Override
    public WbsTaskDO getById(Long id) {
        WbsTaskDO t = wbsTaskMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_c0d8369f");
        }
        return t;
    }

    @Override
    @DataScope(userColumn = "created_by")
    public Page<WbsTaskDO> page(int page, int size, String keyword, String status,
                                String taskType, Long initiationId, Long ownerId) {
        Page<WbsTaskDO> p = new Page<>(page, size);
        LambdaQueryWrapper<WbsTaskDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(WbsTaskDO::getTaskCode, keyword)
                    .or().like(WbsTaskDO::getTaskName, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(WbsTaskDO::getStatus, status);
        if (StringUtils.hasText(taskType)) w.eq(WbsTaskDO::getTaskType, taskType);
        if (initiationId != null) w.eq(WbsTaskDO::getInitiationId, initiationId);
        if (ownerId != null) w.eq(WbsTaskDO::getOwnerId, ownerId);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByAsc(WbsTaskDO::getTaskLevel).orderByAsc(WbsTaskDO::getSortOrder);
        return wbsTaskMapper.selectPage(p, w);
    }

    @Override
    public List<WbsTaskDO> listByInitiation(Long initiationId) {
        return wbsTaskMapper.selectByInitiation(initiationId);
    }

    @Override
    public List<WbsTaskDO> listMilestones(Long initiationId) {
        return wbsTaskMapper.selectMilestones(initiationId);
    }

    @Override
    public BigDecimal calcOverallProgress(Long initiationId) {
        List<WbsTaskDO> list = wbsTaskMapper.selectByInitiation(initiationId);
        if (list == null || list.isEmpty()) return BigDecimal.ZERO;
        BigDecimal totalEffort = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (WbsTaskDO t : list) {
            BigDecimal effort = t.getPlannedEffort() == null ? BigDecimal.ZERO : t.getPlannedEffort();
            BigDecimal progress = t.getProgressPct() == null ? BigDecimal.ZERO : t.getProgressPct();
            totalEffort = totalEffort.add(effort);
            weightedSum = weightedSum.add(effort.multiply(progress));
        }
        if (totalEffort.signum() == 0) return BigDecimal.ZERO;
        return weightedSum.divide(totalEffort, 2, RoundingMode.HALF_UP);
    }

    @Override
    public List<Map<String, Object>> aggregateByStatus(Long initiationId) {
        if (initiationId == null) return List.of();
        return wbsTaskMapper.aggregateByStatus(initiationId);
    }

    private void validate(WbsTaskCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getTaskCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_7839c13b");
        }
        if (!StringUtils.hasText(dto.getTaskName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_f96f7bb7");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_779da94d");
        }
        if (dto.getOwnerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_26804acb");
        }
        if (dto.getPlannedStartDate() != null && dto.getPlannedEndDate() != null
                && dto.getPlannedEndDate().isBefore(dto.getPlannedStartDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_b81e6502");
        }
    }
}
