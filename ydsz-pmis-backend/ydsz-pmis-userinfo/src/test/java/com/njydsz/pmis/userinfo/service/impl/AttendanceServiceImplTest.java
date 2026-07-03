package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.AttendanceCreateDTO;
import com.njydsz.pmis.userinfo.dto.LeaveCreateDTO;
import com.njydsz.pmis.userinfo.dto.OvertimeCreateDTO;
import com.njydsz.pmis.userinfo.entity.AttendanceDO;
import com.njydsz.pmis.userinfo.entity.LeaveDO;
import com.njydsz.pmis.userinfo.entity.OvertimeDO;
import com.njydsz.pmis.userinfo.mapper.AttendanceMapper;
import com.njydsz.pmis.userinfo.mapper.LeaveMapper;
import com.njydsz.pmis.userinfo.mapper.OvertimeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("考勤服务测试")
class AttendanceServiceImplTest {

    @Mock
    private AttendanceMapper attendanceMapper;
    @Mock
    private OvertimeMapper overtimeMapper;
    @Mock
    private LeaveMapper leaveMapper;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @Test
    @DisplayName("登记出勤成功")
    void recordAttendance_shouldInsertAttendance() {
        AttendanceCreateDTO dto = new AttendanceCreateDTO();
        dto.setEmployeeId(1L);
        dto.setAttendanceDate(LocalDate.now());
        dto.setStatus("NORMAL");

        doAnswer(invocation -> {
            AttendanceDO entity = invocation.getArgument(0);
            entity.setId(700L);
            return 1;
        }).when(attendanceMapper).insert(any(AttendanceDO.class));

        Long id = attendanceService.recordAttendance(dto);
        assertNotNull(id);
        assertEquals(700L, id);
        verify(attendanceMapper).insert(any(AttendanceDO.class));
    }

    @Test
    @DisplayName("登记出勤时缺少员工ID抛出异常")
    void recordAttendance_missingEmployeeId_shouldThrowException() {
        AttendanceCreateDTO dto = new AttendanceCreateDTO();
        dto.setAttendanceDate(LocalDate.now());

        BizException ex = assertThrows(BizException.class, () -> attendanceService.recordAttendance(dto));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("分页查询出勤记录")
    void pageAttendance_shouldReturnPagedResult() {
        Page<AttendanceDO> mockPage = new Page<>(1, 10);
        AttendanceDO record = new AttendanceDO();
        record.setId(1L);
        record.setEmployeeId(1L);
        mockPage.setRecords(List.of(record));
        mockPage.setTotal(1);

        when(attendanceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<AttendanceDO> result = attendanceService.pageAttendance(1L, null, null, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("提交加班申请成功")
    void submitOvertime_shouldInsertOvertime() {
        OvertimeCreateDTO dto = new OvertimeCreateDTO();
        dto.setEmployeeId(1L);
        dto.setOvertimeDate(LocalDate.now());
        dto.setStartTime(LocalDateTime.of(2026, 7, 4, 18, 0));
        dto.setEndTime(LocalDateTime.of(2026, 7, 4, 21, 0));
        dto.setOvertimeType("WORKDAY");

        doAnswer(invocation -> {
            OvertimeDO entity = invocation.getArgument(0);
            entity.setId(701L);
            return 1;
        }).when(overtimeMapper).insert(any(OvertimeDO.class));

        Long id = attendanceService.submitOvertime(dto);
        assertNotNull(id);
        assertEquals(701L, id);
        verify(overtimeMapper).insert(any(OvertimeDO.class));
    }

    @Test
    @DisplayName("提交加班申请时缺少员工ID抛出异常")
    void submitOvertime_missingEmployeeId_shouldThrowException() {
        OvertimeCreateDTO dto = new OvertimeCreateDTO();
        dto.setOvertimeDate(LocalDate.now());

        BizException ex = assertThrows(BizException.class, () -> attendanceService.submitOvertime(dto));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("提交请假申请成功")
    void submitLeave_shouldInsertLeave() {
        LeaveCreateDTO dto = new LeaveCreateDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveType("ANNUAL");
        dto.setStartDate(LocalDate.of(2026, 7, 6));
        dto.setEndDate(LocalDate.of(2026, 7, 8));

        doAnswer(invocation -> {
            LeaveDO entity = invocation.getArgument(0);
            entity.setId(702L);
            return 1;
        }).when(leaveMapper).insert(any(LeaveDO.class));

        Long id = attendanceService.submitLeave(dto);
        assertNotNull(id);
        assertEquals(702L, id);
        verify(leaveMapper).insert(any(LeaveDO.class));
    }

    @Test
    @DisplayName("提交请假申请时请假类型无效抛出异常")
    void submitLeave_invalidType_shouldThrowException() {
        LeaveCreateDTO dto = new LeaveCreateDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveType("INVALID_TYPE");
        dto.setStartDate(LocalDate.of(2026, 7, 6));
        dto.setEndDate(LocalDate.of(2026, 7, 8));

        BizException ex = assertThrows(BizException.class, () -> attendanceService.submitLeave(dto));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("考勤状态统计")
    void statByStatus_shouldReturnStatistics() {
        when(attendanceMapper.statByStatus(anyLong(), any(), any())).thenReturn(Collections.emptyList());

        var result = attendanceService.statByStatus(1L, LocalDate.now().minusDays(7), LocalDate.now());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}