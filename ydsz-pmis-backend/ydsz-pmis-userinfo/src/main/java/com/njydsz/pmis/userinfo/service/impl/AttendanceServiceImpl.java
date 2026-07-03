package com.njydsz.pmis.userinfo.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.AttendanceCreateDTO;
import com.njydsz.pmis.userinfo.dto.LeaveCreateDTO;
import com.njydsz.pmis.userinfo.dto.OvertimeCreateDTO;
import com.njydsz.pmis.userinfo.entity.AttendanceDO;
import com.njydsz.pmis.userinfo.entity.LeaveDO;
import com.njydsz.pmis.userinfo.entity.OvertimeDO;
import com.njydsz.pmis.userinfo.enums.AttendanceStatus;
import com.njydsz.pmis.userinfo.enums.LeaveStatus;
import com.njydsz.pmis.userinfo.enums.LeaveType;
import com.njydsz.pmis.userinfo.mapper.AttendanceMapper;
import com.njydsz.pmis.userinfo.mapper.LeaveMapper;
import com.njydsz.pmis.userinfo.mapper.OvertimeMapper;
import com.njydsz.pmis.userinfo.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 考勤服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final OvertimeMapper overtimeMapper;
    private final LeaveMapper leaveMapper;

    // ==================== 出勤 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordAttendance(AttendanceCreateDTO dto) {
        validateAttendance(dto);

        // 1. 计算工作时长 (若给了 checkIn/checkOut)
        if (dto.getCheckInTime() != null && dto.getCheckOutTime() != null && dto.getCheckOutTime().isAfter(dto.getCheckInTime())) {
            long minutes = Duration.between(dto.getCheckInTime(), dto.getCheckOutTime()).toMinutes();
            BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            if (dto.getWorkHours() == null) dto.setWorkHours(hours);
        }

        // 2. 状态自动识别
        if (!StringUtils.hasText(dto.getStatus())) {
            dto.setStatus(AttendanceStatus.NORMAL.getCode());
        }

        AttendanceDO entity = new AttendanceDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getWorkHours() == null) entity.setWorkHours(BigDecimal.ZERO);
        if (entity.getOvertimeHours() == null) entity.setOvertimeHours(BigDecimal.ZERO);
        if (entity.getTenantId() == null) entity.setTenantId(1L);
        if (!StringUtils.hasText(entity.getProviderTraceId())) entity.setProviderTraceId("");

        attendanceMapper.insert(entity);
        log.info("[Attendance] 登记出勤: emp={} date={} status={}",
                entity.getEmployeeId(), entity.getAttendanceDate(), entity.getStatus());
        return entity.getId();
    }

    @Override
    public Page<AttendanceDO> pageAttendance(Long employeeId, LocalDate startDate, LocalDate endDate, int page, int size) {
        Page<AttendanceDO> p = new Page<>(page, size);
        LambdaQueryWrapper<AttendanceDO> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(AttendanceDO::getEmployeeId, employeeId);
        if (startDate != null) wrapper.ge(AttendanceDO::getAttendanceDate, startDate);
        if (endDate != null) wrapper.le(AttendanceDO::getAttendanceDate, endDate);
        wrapper.orderByDesc(AttendanceDO::getAttendanceDate);
        return attendanceMapper.selectPage(p, wrapper);
    }

    @Override
    public List<Map<String, Object>> statByStatus(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return attendanceMapper.statByStatus(employeeId, startDate, endDate);
    }

    // ==================== 加班 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitOvertime(OvertimeCreateDTO dto) {
        validateOvertime(dto);

        // 自动计算加班时长
        if (dto.getOvertimeHours() == null && dto.getStartTime() != null && dto.getEndTime() != null) {
            long minutes = Duration.between(dto.getStartTime(), dto.getEndTime()).toMinutes();
            if (minutes <= 0) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_0e756b4f");
            dto.setOvertimeHours(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }
        if (dto.getPayRate() == null) dto.setPayRate(new BigDecimal("1.5"));

        OvertimeDO entity = new OvertimeDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getOvertimeCode() == null) entity.setOvertimeCode("OT-" + IdUtil.fastSimpleUUID());
        entity.setApprovalStatus("DRAFT");
        if (entity.getTenantId() == null) entity.setTenantId(1L);
        if (!StringUtils.hasText(entity.getProviderTraceId())) entity.setProviderTraceId("");

        overtimeMapper.insert(entity);
        log.info("[Overtime] 提交加班: code={} emp={} hours={}",
                entity.getOvertimeCode(), entity.getEmployeeId(), entity.getOvertimeHours());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOvertime(Long id, String action, String approverId, String approverName, String remark) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_411b6827");
        if (!"APPROVED".equalsIgnoreCase(action) && !"REJECTED".equalsIgnoreCase(action)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_dbf45b98");
        }
        OvertimeDO entity = overtimeMapper.selectById(id);
        if (entity == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_09aca734");
        if (!"SUBMITTED".equalsIgnoreCase(entity.getApprovalStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_8a0e5737" + entity.getApprovalStatus());
        }
        entity.setApprovalStatus(action.toUpperCase());
        entity.setApproverId(approverId == null ? null : Long.valueOf(approverId));
        entity.setApproverName(approverName);
        entity.setApprovalTime(LocalDateTime.now());
        entity.setApprovalRemark(remark);
        overtimeMapper.updateById(entity);
    }

    @Override
    public Page<OvertimeDO> pageOvertime(Long employeeId, String approvalStatus, int page, int size) {
        Page<OvertimeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OvertimeDO> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(OvertimeDO::getEmployeeId, employeeId);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(OvertimeDO::getApprovalStatus, approvalStatus);
        wrapper.orderByDesc(OvertimeDO::getOvertimeDate);
        return overtimeMapper.selectPage(p, wrapper);
    }

    @Override
    public OvertimeDO getOvertime(Long id) {
        return id == null ? null : overtimeMapper.selectById(id);
    }

    // ==================== 请假 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitLeave(LeaveCreateDTO dto) {
        validateLeave(dto);

        // 自动计算请假天数
        if (dto.getLeaveDays() == null && dto.getStartDate() != null && dto.getEndDate() != null) {
            long days = Duration.between(dto.getStartDate().atStartOfDay(), dto.getEndDate().atStartOfDay()).toDays() + 1;
            if (days <= 0) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_6ea170d7");
            dto.setLeaveDays(BigDecimal.valueOf(days));
        }

        LeaveDO entity = new LeaveDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getLeaveCode() == null) entity.setLeaveCode("LV-" + IdUtil.fastSimpleUUID());
        entity.setApprovalStatus(LeaveStatus.DRAFT.getCode());
        if (entity.getTenantId() == null) entity.setTenantId(1L);
        if (!StringUtils.hasText(entity.getProviderTraceId())) entity.setProviderTraceId("");

        leaveMapper.insert(entity);
        log.info("[Leave] 提交请假: code={} emp={} type={} days={}",
                entity.getLeaveCode(), entity.getEmployeeId(), entity.getLeaveType(), entity.getLeaveDays());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveLeave(Long id, String action, String approverId, String approverName, String remark) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_411b6827");
        LeaveDO entity = leaveMapper.selectById(id);
        if (entity == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_802c6117");
        LeaveStatus current = LeaveStatus.fromCode(entity.getApprovalStatus());
        LeaveStatus target = LeaveStatus.fromCode(action);
        if (current == null || target == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_555b7349" + action);
        }
        if (!current.canTransitTo(target)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.user.msg_e6729e07" + current.getDesc() + " 流转到 " + target.getDesc());
        }
        entity.setApprovalStatus(target.getCode());
        if (approverId != null) entity.setApproverId(Long.valueOf(approverId));
        entity.setApproverName(approverName);
        entity.setApprovalTime(LocalDateTime.now());
        entity.setApprovalRemark(remark);
        leaveMapper.updateById(entity);
    }

    @Override
    public Page<LeaveDO> pageLeave(Long employeeId, String approvalStatus, int page, int size) {
        Page<LeaveDO> p = new Page<>(page, size);
        LambdaQueryWrapper<LeaveDO> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(LeaveDO::getEmployeeId, employeeId);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(LeaveDO::getApprovalStatus, approvalStatus);
        wrapper.orderByDesc(LeaveDO::getStartDate);
        return leaveMapper.selectPage(p, wrapper);
    }

    @Override
    public LeaveDO getLeave(Long id) {
        return id == null ? null : leaveMapper.selectById(id);
    }

    @Override
    public List<LeaveDO> listApprovedLeaves(Long employeeId, LocalDate startDate, LocalDate endDate) {
        if (employeeId == null || startDate == null || endDate == null) return List.of();
        return leaveMapper.selectApprovedByEmployeeAndRange(
                employeeId, startDate.toString(), endDate.toString());
    }

    // ==================== 校验 ====================

    private void validateAttendance(AttendanceCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (dto.getEmployeeId() == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_03f5ae35");
        if (dto.getAttendanceDate() == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_6d57c0a5");
        if (StringUtils.hasText(dto.getStatus()) && AttendanceStatus.fromCode(dto.getStatus()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_555b7349" + dto.getStatus());
        }
    }

    private void validateOvertime(OvertimeCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (dto.getEmployeeId() == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_03f5ae35");
        if (dto.getOvertimeDate() == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_f8aecb6a");
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_a765717d");
        }
        if (!StringUtils.hasText(dto.getOvertimeType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_1f6cd674");
        }
    }

    private void validateLeave(LeaveCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (dto.getEmployeeId() == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_03f5ae35");
        if (LeaveType.fromCode(dto.getLeaveType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_867f50ca" + dto.getLeaveType());
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_9c779eb8");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_7e6b1218");
        }
    }
}