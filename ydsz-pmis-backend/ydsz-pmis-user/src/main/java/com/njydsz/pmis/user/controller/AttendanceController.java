package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.AttendanceCreateDTO;
import com.njydsz.pmis.user.dto.LeaveCreateDTO;
import com.njydsz.pmis.user.dto.OvertimeCreateDTO;
import com.njydsz.pmis.user.entity.AttendanceDO;
import com.njydsz.pmis.user.entity.LeaveDO;
import com.njydsz.pmis.user.entity.OvertimeDO;
import com.njydsz.pmis.user.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 考勤接口
 *
 * <p>覆盖出勤/加班/请假 三类记录。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "考勤管理")
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ============== 出勤 ==============

    @Operation(summary = "登记出勤")
    @PrePermission("attendance:record:create")
    @OperationLog(module = "考勤", action = "登记出勤", bizType = "ATTENDANCE")
    @PostMapping("/record")
    public Result<Long> recordAttendance(@Valid @RequestBody AttendanceCreateDTO dto) {
        return Result.ok(attendanceService.recordAttendance(dto));
    }

    @Operation(summary = "出勤分页")
    @PrePermission("attendance:record:list")
    @GetMapping("/record/page")
    public Result<Page<AttendanceDO>> pageAttendance(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.pageAttendance(employeeId, startDate, endDate, page, size));
    }

    @Operation(summary = "出勤状态统计")
    @GetMapping("/record/stat")
    public Result<List<Map<String, Object>>> statByStatus(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(attendanceService.statByStatus(employeeId, startDate, endDate));
    }

    // ============== 加班 ==============

    @Operation(summary = "提交加班申请")
    @PrePermission("attendance:overtime:create")
    @OperationLog(module = "考勤", action = "提交加班", bizType = "OVERTIME")
    @PostMapping("/overtime")
    public Result<Long> submitOvertime(@Valid @RequestBody OvertimeCreateDTO dto) {
        return Result.ok(attendanceService.submitOvertime(dto));
    }

    @Operation(summary = "审批加班")
    @PrePermission("attendance:overtime:approve")
    @OperationLog(module = "考勤", action = "审批加班", bizType = "OVERTIME")
    @PostMapping("/overtime/{id}/approve")
    public Result<Void> approveOvertime(
            @PathVariable Long id,
            @RequestParam String action,
            @RequestHeader(value = "X-User-Id", required = false) String approverId,
            @RequestHeader(value = "X-Username", required = false) String approverName,
            @RequestParam(required = false) String remark) {
        attendanceService.approveOvertime(id, action, approverId, approverName, remark);
        return Result.ok();
    }

    @Operation(summary = "加班分页")
    @PrePermission("attendance:overtime:list")
    @GetMapping("/overtime/page")
    public Result<Page<OvertimeDO>> pageOvertime(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.pageOvertime(employeeId, approvalStatus, page, size));
    }

    @Operation(summary = "加班详情")
    @GetMapping("/overtime/{id}")
    public Result<OvertimeDO> getOvertime(@PathVariable Long id) {
        return Result.ok(attendanceService.getOvertime(id));
    }

    // ============== 请假 ==============

    @Operation(summary = "提交请假申请")
    @PrePermission("attendance:leave:create")
    @OperationLog(module = "考勤", action = "提交请假", bizType = "LEAVE")
    @PostMapping("/leave")
    public Result<Long> submitLeave(@Valid @RequestBody LeaveCreateDTO dto) {
        return Result.ok(attendanceService.submitLeave(dto));
    }

    @Operation(summary = "审批请假")
    @PrePermission("attendance:leave:approve")
    @OperationLog(module = "考勤", action = "审批请假", bizType = "LEAVE")
    @PostMapping("/leave/{id}/approve")
    public Result<Void> approveLeave(
            @PathVariable Long id,
            @RequestParam String action,
            @RequestHeader(value = "X-User-Id", required = false) String approverId,
            @RequestHeader(value = "X-Username", required = false) String approverName,
            @RequestParam(required = false) String remark) {
        attendanceService.approveLeave(id, action, approverId, approverName, remark);
        return Result.ok();
    }

    @Operation(summary = "请假分页")
    @PrePermission("attendance:leave:list")
    @GetMapping("/leave/page")
    public Result<Page<LeaveDO>> pageLeave(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.pageLeave(employeeId, approvalStatus, page, size));
    }

    @Operation(summary = "请假详情")
    @GetMapping("/leave/{id}")
    public Result<LeaveDO> getLeave(@PathVariable Long id) {
        return Result.ok(attendanceService.getLeave(id));
    }

    @Operation(summary = "员工在指定日期内已批准的请假")
    @GetMapping("/leave/approved")
    public Result<List<LeaveDO>> listApprovedLeaves(
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(attendanceService.listApprovedLeaves(employeeId, startDate, endDate));
    }
}
