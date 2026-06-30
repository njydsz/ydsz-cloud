package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.user.engine.UtilizationCalculator;
import com.njydsz.pmis.user.entity.ResourceAssignmentDO;
import com.njydsz.pmis.user.enums.AssignmentStatus;
import com.njydsz.pmis.user.mapper.ResourceAssignmentMapper;
import com.njydsz.pmis.user.service.ResourceAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceAssignmentServiceImpl implements ResourceAssignmentService {

    private final ResourceAssignmentMapper assignmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long act(ResourceAssignmentCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getAction())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "动作不能为空");
        }
        if (dto.getEmployeeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "员工 ID 不能为空");
        }
        if (assignmentMapper.selectByCode(dto.getAssignmentCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "分配编号已存在: " + dto.getAssignmentCode());
        }
        // RESERVE 阶段要求 opportunity 或 initiation 任一存在
        String action = dto.getAction().toUpperCase();
        if ("RESERVE".equals(action) && dto.getOpportunityId() == null && dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预占必须关联商机或项目");
        }
        // START/TRANSFER/RELEASE 阶段要求 initiation
        if (("START".equals(action) || "TRANSFER".equals(action) || "RELEASE".equals(action))
                && dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "入场/调岗/离场必须关联项目");
        }
        // 过载检测
        if ("START".equals(action) || "RESERVE".equals(action)) {
            int active = assignmentMapper.countActiveByEmployee(dto.getEmployeeId());
            if (UtilizationCalculator.isOverloaded(active + 1)) {
                log.warn("[Resource] 员工过载预警: emp={} active={}", dto.getEmployeeId(), active + 1);
            }
        }
        ResourceAssignmentDO a = new ResourceAssignmentDO();
        BeanUtils.copyProperties(dto, a);
        a.setStatus(mapActionToStatus(action));
        if (a.getAllocation() == null) a.setAllocation(new BigDecimal("1.0"));
        if (a.getDailyHours() == null) a.setDailyHours(new BigDecimal("8.0"));
        if (a.getBillable() == null) a.setBillable(1);
        if (a.getTenantId() == null) a.setTenantId(1L);
        if (a.getProviderTraceId() == null) a.setProviderTraceId("");
        if ("START".equals(action) && a.getActualStartDate() == null) {
            a.setActualStartDate(LocalDate.now());
        }
        if ("RELEASE".equals(action) && a.getActualEndDate() == null) {
            a.setActualEndDate(LocalDate.now());
        }
        assignmentMapper.insert(a);
        log.info("[Resource] 分配: code={} emp={} action={} status={}",
                a.getAssignmentCode(), a.getEmployeeId(), action, a.getStatus());
        return a.getId();
    }

    @Override
    public ResourceAssignmentDO getById(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        ResourceAssignmentDO a = assignmentMapper.selectById(id);
        if (a == null) throw new BizException(BizErrorCode.NOT_FOUND, "分配记录不存在");
        return a;
    }

    @Override
    public List<ResourceAssignmentDO> listByEmployee(Long employeeId) {
        if (employeeId == null) return List.of();
        return assignmentMapper.selectByEmployee(employeeId);
    }

    @Override
    public List<ResourceAssignmentDO> listByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return assignmentMapper.selectByInitiation(initiationId);
    }

    @Override
    public int activeCount(Long employeeId) {
        if (employeeId == null) return 0;
        Integer c = assignmentMapper.countActiveByEmployee(employeeId);
        return c == null ? 0 : c;
    }

    @Override
    public Map<String, Object> utilization(Long employeeId) {
        Map<String, Object> out = new HashMap<>();
        if (employeeId == null) return out;
        List<ResourceAssignmentDO> all = assignmentMapper.selectByEmployee(employeeId);
        BigDecimal totalAllocation = BigDecimal.ZERO;
        int active = 0;
        for (ResourceAssignmentDO a : all) {
            String s = a.getStatus();
            if ("RESERVED".equals(s) || "ACTIVE".equals(s) || "TRANSFERRING".equals(s)) {
                if (a.getAllocation() != null) totalAllocation = totalAllocation.add(a.getAllocation());
                active++;
            }
        }
        out.put("activeCount", active);
        out.put("totalAllocation", totalAllocation);
        out.put("overloaded", UtilizationCalculator.isOverloaded(active));
        out.put("utilizationLevel",
                UtilizationCalculator.utilizationLevel(
                        totalAllocation.compareTo(new BigDecimal("1.0")) > 0
                                ? new BigDecimal("1.0")
                                : totalAllocation));
        return out;
    }

    @Override
    public Page<ResourceAssignmentDO> page(int page, int size, Long employeeId, Long initiationId, String status) {
        Page<ResourceAssignmentDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ResourceAssignmentDO> w = new LambdaQueryWrapper<>();
        if (employeeId != null) w.eq(ResourceAssignmentDO::getEmployeeId, employeeId);
        if (initiationId != null) w.eq(ResourceAssignmentDO::getInitiationId, initiationId);
        if (StringUtils.hasText(status)) w.eq(ResourceAssignmentDO::getStatus, status);
        w.orderByDesc(ResourceAssignmentDO::getCreatedAt);
        return assignmentMapper.selectPage(p, w);
    }

    private String mapActionToStatus(String action) {
        return switch (action) {
            case "RESERVE" -> AssignmentStatus.RESERVED.getCode();
            case "START" -> AssignmentStatus.ACTIVE.getCode();
            case "TRANSFER" -> AssignmentStatus.TRANSFERRING.getCode();
            case "RELEASE" -> AssignmentStatus.RELEASED.getCode();
            case "CANCEL" -> AssignmentStatus.CANCELLED.getCode();
            default -> AssignmentStatus.RESERVED.getCode();
        };
    }
}
