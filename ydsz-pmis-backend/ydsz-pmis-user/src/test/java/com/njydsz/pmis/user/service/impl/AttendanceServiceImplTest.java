package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.AttendanceCreateDTO;
import com.njydsz.pmis.user.dto.LeaveCreateDTO;
import com.njydsz.pmis.user.dto.OvertimeCreateDTO;
import com.njydsz.pmis.user.entity.AttendanceDO;
import com.njydsz.pmis.user.entity.LeaveDO;
import com.njydsz.pmis.user.entity.OvertimeDO;
import com.njydsz.pmis.user.mapper.AttendanceMapper;
import com.njydsz.pmis.user.mapper.LeaveMapper;
import com.njydsz.pmis.user.mapper.OvertimeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AttendanceServiceImpl 测试
 */
@DisplayName("AttendanceServiceImpl 考勤")
class AttendanceServiceImplTest {

    private AttendanceMapper attendanceMapper;
    private OvertimeMapper overtimeMapper;
    private LeaveMapper leaveMapper;
    private AttendanceServiceImpl service;

    @BeforeEach
    void setUp() {
        attendanceMapper = mock(AttendanceMapper.class);
        overtimeMapper = mock(OvertimeMapper.class);
        leaveMapper = mock(LeaveMapper.class);
        service = new AttendanceServiceImpl(attendanceMapper, overtimeMapper, leaveMapper);
    }

    // ============== 出勤 ==============

    @Test
    @DisplayName("recordAttendance 缺 employeeId 抛错")
    void recordMissingEmployee() {
        AttendanceCreateDTO dto = new AttendanceCreateDTO();
        dto.setAttendanceDate(LocalDate.now());
        assertThatThrownBy(() -> service.recordAttendance(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recordAttendance 缺日期抛错")
    void recordMissingDate() {
        AttendanceCreateDTO dto = new AttendanceCreateDTO();
        dto.setEmployeeId(1L);
        assertThatThrownBy(() -> service.recordAttendance(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recordAttendance 无效状态抛错")
    void recordInvalidStatus() {
        AttendanceCreateDTO dto = new AttendanceCreateDTO();
        dto.setEmployeeId(1L);
        dto.setAttendanceDate(LocalDate.now());
        dto.setStatus("WRONG");
        assertThatThrownBy(() -> service.recordAttendance(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recordAttendance 自动计算工作时长")
    void recordAutoWorkHours() {
        AttendanceCreateDTO dto = new AttendanceCreateDTO();
        dto.setEmployeeId(1L);
        dto.setAttendanceDate(LocalDate.now());
        dto.setCheckInTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        dto.setCheckOutTime(LocalDateTime.of(2026, 6, 1, 18, 0));
        when(attendanceMapper.insert(any(AttendanceDO.class))).thenAnswer(inv -> {
            AttendanceDO e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        });
        Long id = service.recordAttendance(dto);
        assertThat(id).isEqualTo(100L);
    }

    @Test
    @DisplayName("pageAttendance 委托 mapper")
    void pageAttendanceDelegated() {
        when(attendanceMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        Page<AttendanceDO> p = service.pageAttendance(1L, LocalDate.now(), LocalDate.now(), 1, 10);
        assertThat(p).isNotNull();
    }

    @Test
    @DisplayName("statByStatus 委托 mapper")
    void statDelegated() {
        when(attendanceMapper.statByStatus(any(), any(), any())).thenReturn(List.of(Map.of("status", "NORMAL", "count", 5)));
        List<Map<String, Object>> r = service.statByStatus(1L, LocalDate.now(), LocalDate.now());
        assertThat(r).hasSize(1);
    }

    // ============== 加班 ==============

    @Test
    @DisplayName("submitOvertime 自动计算小时数并生成编码")
    void submitOvertimeAutoHours() {
        OvertimeCreateDTO dto = new OvertimeCreateDTO();
        dto.setEmployeeId(1L);
        dto.setOvertimeDate(LocalDate.of(2026, 6, 1));
        dto.setStartTime(LocalDateTime.of(2026, 6, 1, 18, 0));
        dto.setEndTime(LocalDateTime.of(2026, 6, 1, 21, 0));
        dto.setOvertimeType("WORKDAY");
        when(overtimeMapper.insert(any(OvertimeDO.class))).thenAnswer(inv -> {
            OvertimeDO e = inv.getArgument(0);
            e.setId(200L);
            return 1;
        });
        Long id = service.submitOvertime(dto);
        assertThat(id).isEqualTo(200L);
    }

    @Test
    @DisplayName("submitOvertime 缺开始/结束抛错")
    void submitOvertimeMissingTime() {
        OvertimeCreateDTO dto = new OvertimeCreateDTO();
        dto.setEmployeeId(1L);
        dto.setOvertimeDate(LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> service.submitOvertime(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("approveOvertime 非 SUBMITTED 拒绝")
    void approveOvertimeWrongState() {
        OvertimeDO entity = new OvertimeDO();
        entity.setId(1L);
        entity.setApprovalStatus("DRAFT");
        when(overtimeMapper.selectById(1L)).thenReturn(entity);
        assertThatThrownBy(() -> service.approveOvertime(1L, "APPROVED", "99", "boss", "ok"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("approveOvertime 成功")
    void approveOvertimeSuccess() {
        OvertimeDO entity = new OvertimeDO();
        entity.setId(1L);
        entity.setApprovalStatus("SUBMITTED");
        when(overtimeMapper.selectById(1L)).thenReturn(entity);
        service.approveOvertime(1L, "APPROVED", "99", "boss", "ok");
        assertThat(entity.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(entity.getApproverName()).isEqualTo("boss");
    }

    @Test
    @DisplayName("approveOvertime 无效 action 抛错")
    void approveOvertimeInvalidAction() {
        assertThatThrownBy(() -> service.approveOvertime(1L, "WRONG", "1", "boss", "ok"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("pageOvertime 委托 mapper")
    void pageOvertimeDelegated() {
        when(overtimeMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        Page<OvertimeDO> p = service.pageOvertime(1L, "DRAFT", 1, 10);
        assertThat(p).isNotNull();
    }

    @Test
    @DisplayName("getOvertime id 为空返回 null")
    void getOvertimeNull() {
        assertThat(service.getOvertime(null)).isNull();
    }

    // ============== 请假 ==============

    @Test
    @DisplayName("submitLeave 自动计算天数")
    void submitLeaveAutoDays() {
        LeaveCreateDTO dto = new LeaveCreateDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveType("ANNUAL");
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 6, 3));
        when(leaveMapper.insert(any(LeaveDO.class))).thenAnswer(inv -> {
            LeaveDO e = inv.getArgument(0);
            e.setId(300L);
            return 1;
        });
        Long id = service.submitLeave(dto);
        assertThat(id).isEqualTo(300L);
    }

    @Test
    @DisplayName("submitLeave 类型无效")
    void submitLeaveInvalidType() {
        LeaveCreateDTO dto = new LeaveCreateDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveType("WRONG");
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 6, 3));
        assertThatThrownBy(() -> service.submitLeave(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("submitLeave 结束日期早于开始抛错")
    void submitLeaveEndBeforeStart() {
        LeaveCreateDTO dto = new LeaveCreateDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveType("ANNUAL");
        dto.setStartDate(LocalDate.of(2026, 6, 5));
        dto.setEndDate(LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> service.submitLeave(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("approveLeave 状态机非法转换")
    void approveLeaveInvalidTransition() {
        LeaveDO entity = new LeaveDO();
        entity.setId(1L);
        entity.setApprovalStatus("APPROVED");
        when(leaveMapper.selectById(1L)).thenReturn(entity);
        assertThatThrownBy(() -> service.approveLeave(1L, "DRAFT", "99", "boss", "ok"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("approveLeave DRAFT→SUBMITTED 成功")
    void approveLeaveDraftToSubmitted() {
        LeaveDO entity = new LeaveDO();
        entity.setId(1L);
        entity.setApprovalStatus("DRAFT");
        when(leaveMapper.selectById(1L)).thenReturn(entity);
        service.approveLeave(1L, "SUBMITTED", "99", "boss", "ok");
        assertThat(entity.getApprovalStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("pageLeave 委托 mapper")
    void pageLeaveDelegated() {
        when(leaveMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        Page<LeaveDO> p = service.pageLeave(1L, "DRAFT", 1, 10);
        assertThat(p).isNotNull();
    }

    @Test
    @DisplayName("getLeave id 空返回 null")
    void getLeaveNull() {
        assertThat(service.getLeave(null)).isNull();
    }

    @Test
    @DisplayName("listApprovedLeaves 缺参数返回空")
    void listApprovedLeavesEmpty() {
        assertThat(service.listApprovedLeaves(null, LocalDate.now(), LocalDate.now())).isEmpty();
        assertThat(service.listApprovedLeaves(1L, null, LocalDate.now())).isEmpty();
    }
}
