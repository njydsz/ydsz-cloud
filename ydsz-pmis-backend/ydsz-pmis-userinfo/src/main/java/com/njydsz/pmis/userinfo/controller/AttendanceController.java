package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.AttendanceCreateDTO;
import com.njydsz.pmis.userinfo.dto.LeaveCreateDTO;
import com.njydsz.pmis.userinfo.dto.OvertimeCreateDTO;
import com.njydsz.pmis.userinfo.entity.AttendanceDO;
import com.njydsz.pmis.userinfo.entity.LeaveDO;
import com.njydsz.pmis.userinfo.entity.OvertimeDO;
import com.njydsz.pmis.userinfo.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Validated
public class AttendanceController {

    /** 考勤服务 */
    private final AttendanceService attendanceService;

    // ============== 出勤 ==============

    /**
     * 登记出勤
     *
     * @param dto 出勤登记参数
     * @return 统一响应结果，包含出勤记录 ID
     */
    @Operation(summary = "登记出勤")
    @PrePermission("attendance:record:create")
    @OperationLog(module = "考勤", action = "登记出勤", bizType = "ATTENDANCE")
    @Idempotent(key = "attendance:record-attendance", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/record")
    public Result<String> recordAttendance(@Valid @RequestBody AttendanceCreateDTO dto) {
        return Result.ok(attendanceService.recordAttendance(dto));
    }

    /**
     * 出勤记录分页查询
     *
     * @param employeeId 员工 ID（可选）
     * @param startDate  起始日期（可选）
     * @param endDate    截止日期（可选）
     * @param page       页码
     * @param size       每页大小
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "出勤分页")
    @PrePermission("attendance:record:list")
    @GetMapping("/record/page")
    public Result<Page<AttendanceDO>> pageAttendance(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(attendanceService.pageAttendance(employeeId, startDate, endDate, page, size));
    }

    /**
     * 出勤状态统计
     *
     * @param employeeId 员工 ID（可选）
     * @param startDate  起始日期（可选）
     * @param endDate    截止日期（可选）
     * @return 统一响应结果，包含按状态汇总数据
     */
    @Operation(summary = "出勤状态统计")
    @GetMapping("/record/stat")
    public Result<List<Map<String, Object>>> statByStatus(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(attendanceService.statByStatus(employeeId, startDate, endDate));
    }

    // ============== 加班 ==============

    /**
     * 提交加班申请
     *
     * @param dto 加班申请参数
     * @return 统一响应结果，包含加班记录 ID
     */
    @Operation(summary = "提交加班申请")
    @PrePermission("attendance:overtime:create")
    @OperationLog(module = "考勤", action = "提交加班", bizType = "OVERTIME")
    @Idempotent(key = "attendance:submit-overtime", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/overtime")
    public Result<String> submitOvertime(@Valid @RequestBody OvertimeCreateDTO dto) {
        return Result.ok(attendanceService.submitOvertime(dto));
    }

    /**
     * 审批加班
     *
     * @param id           加班记录 ID
     * @param action       审批动作（APPROVE/REJECT）
     * @param approverId   审批人 ID（由网关透传）
     * @param approverName 审批人姓名（由网关透传）
     * @param remark       审批备注（可选）
     * @return 统一响应结果
     */
    @Operation(summary = "审批加班")
    @PrePermission("attendance:overtime:approve")
    @OperationLog(module = "考勤", action = "审批加班", bizType = "OVERTIME")
    @Idempotent(key = "attendance:approve-overtime", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/overtime/{id}/approve")
    public Result<Void> approveOvertime(
            @PathVariable @NotBlank String id,
            @RequestParam String action,
            @RequestHeader(value = "X-User-Id", required = false) String approverId,
            @RequestHeader(value = "X-Username", required = false) String approverName,
            @RequestParam(required = false) String remark) {
        attendanceService.approveOvertime(id, action, approverId, approverName, remark);
        return Result.ok();
    }

    /**
     * 加班记录分页查询
     *
     * @param employeeId     员工 ID（可选）
     * @param approvalStatus 审批状态（可选）
     * @param page           页码
     * @param size           每页大小
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "加班分页")
    @PrePermission("attendance:overtime:list")
    @GetMapping("/overtime/page")
    public Result<Page<OvertimeDO>> pageOvertime(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(attendanceService.pageOvertime(employeeId, approvalStatus, page, size));
    }

    /**
     * 查询加班详情
     *
     * @param id 加班记录 ID
     * @return 统一响应结果，包含加班记录
     */
    @Operation(summary = "加班详情")
    @GetMapping("/overtime/{id}")
    public Result<OvertimeDO> getOvertime(@PathVariable @NotBlank String id) {
        return Result.ok(attendanceService.getOvertime(id));
    }

    // ============== 请假 ==============

    /**
     * 提交请假申请
     *
     * @param dto 请假申请参数
     * @return 统一响应结果，包含请假记录 ID
     */
    @Operation(summary = "提交请假申请")
    @PrePermission("attendance:leave:create")
    @OperationLog(module = "考勤", action = "提交请假", bizType = "LEAVE")
    @Idempotent(key = "attendance:submit-leave", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/leave")
    public Result<String> submitLeave(@Valid @RequestBody LeaveCreateDTO dto) {
        return Result.ok(attendanceService.submitLeave(dto));
    }

    /**
     * 审批请假
     *
     * @param id           请假记录 ID
     * @param action       审批动作（APPROVE/REJECT）
     * @param approverId   审批人 ID（由网关透传）
     * @param approverName 审批人姓名（由网关透传）
     * @param remark       审批备注（可选）
     * @return 统一响应结果
     */
    @Operation(summary = "审批请假")
    @PrePermission("attendance:leave:approve")
    @OperationLog(module = "考勤", action = "审批请假", bizType = "LEAVE")
    @Idempotent(key = "attendance:approve-leave", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/leave/{id}/approve")
    public Result<Void> approveLeave(
            @PathVariable @NotBlank String id,
            @RequestParam String action,
            @RequestHeader(value = "X-User-Id", required = false) String approverId,
            @RequestHeader(value = "X-Username", required = false) String approverName,
            @RequestParam(required = false) String remark) {
        attendanceService.approveLeave(id, action, approverId, approverName, remark);
        return Result.ok();
    }

    /**
     * 请假记录分页查询
     *
     * @param employeeId     员工 ID（可选）
     * @param approvalStatus 审批状态（可选）
     * @param page           页码
     * @param size           每页大小
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "请假分页")
    @PrePermission("attendance:leave:list")
    @GetMapping("/leave/page")
    public Result<Page<LeaveDO>> pageLeave(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(attendanceService.pageLeave(employeeId, approvalStatus, page, size));
    }

    /**
     * 查询请假详情
     *
     * @param id 请假记录 ID
     * @return 统一响应结果，包含请假记录
     */
    @Operation(summary = "请假详情")
    @GetMapping("/leave/{id}")
    public Result<LeaveDO> getLeave(@PathVariable @NotBlank String id) {
        return Result.ok(attendanceService.getLeave(id));
    }

    /**
     * 查询员工在指定日期内已批准的请假记录
     *
     * @param employeeId 员工 ID
     * @param startDate  起始日期
     * @param endDate    截止日期
     * @return 统一响应结果，包含请假记录列表
     */
    @Operation(summary = "员工在指定日期内已批准的请假")
    @GetMapping("/leave/approved")
    public Result<List<LeaveDO>> listApprovedLeaves(
            @RequestParam @NotBlank String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(attendanceService.listApprovedLeaves(employeeId, startDate, endDate));
    }
}
