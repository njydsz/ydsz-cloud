package com.njydsz.pmis.userinfo.web.controller.rate;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.userinfo.domain.dto.rate.AttendanceCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.rate.LeaveCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.rate.OvertimeCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.rate.AttendanceDO;
import com.njydsz.pmis.userinfo.domain.entity.rate.LeaveDO;
import com.njydsz.pmis.userinfo.domain.entity.rate.OvertimeDO;
import com.njydsz.pmis.userinfo.server.service.rate.AttendanceService;
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
    @AuthApiPermission(apiCodes = "attendance:record:create")
    @OperationLog(module = "考勤", action = "登记出勤", bizType = "ATTENDANCE")
    @Idempotent(key = "attendance:recordAttendance", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/record")
    public BaseResponse<String> recordAttendance(@Valid @RequestBody AttendanceCreateDTO dto) {
        return BaseResponse.ok(attendanceService.recordAttendance(dto));
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
    @AuthApiPermission(apiCodes = "attendance:record:list")
    @GetMapping("/record/page")
    public BaseResponse<Page<AttendanceDO>> pageAttendance(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return BaseResponse.ok(attendanceService.pageAttendance(employeeId, startDate, endDate, page, size));
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
    public BaseResponse<List<Map<String, Object>>> statByStatus(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return BaseResponse.ok(attendanceService.statByStatus(employeeId, startDate, endDate));
    }

    // ============== 加班 ==============

    /**
     * 提交加班申请
     *
     * @param dto 加班申请参数
     * @return 统一响应结果，包含加班记录 ID
     */
    @Operation(summary = "提交加班申请")
    @AuthApiPermission(apiCodes = "attendance:overtime:create")
    @OperationLog(module = "考勤", action = "提交加班", bizType = "OVERTIME")
    @Idempotent(key = "attendance:submitOvertime", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/overtime")
    public BaseResponse<String> submitOvertime(@Valid @RequestBody OvertimeCreateDTO dto) {
        return BaseResponse.ok(attendanceService.submitOvertime(dto));
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
    @AuthApiPermission(apiCodes = "attendance:overtime:approve")
    @OperationLog(module = "考勤", action = "审批加班", bizType = "OVERTIME")
    @Idempotent(key = "attendance:approveOvertime", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/overtime/{id}/approve")
    public BaseResponse<Void> approveOvertime(
            @PathVariable @NotBlank String id,
            @RequestParam String action,
            @RequestHeader(value = "X-User-Id", required = false) String approverId,
            @RequestHeader(value = "X-Username", required = false) String approverName,
            @RequestParam(required = false) String remark) {
        attendanceService.approveOvertime(id, action, approverId, approverName, remark);
        return BaseResponse.ok();
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
    @AuthApiPermission(apiCodes = "attendance:overtime:list")
    @GetMapping("/overtime/page")
    public BaseResponse<Page<OvertimeDO>> pageOvertime(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return BaseResponse.ok(attendanceService.pageOvertime(employeeId, approvalStatus, page, size));
    }

    /**
     * 查询加班详情
     *
     * @param id 加班记录 ID
     * @return 统一响应结果，包含加班记录
     */
    @Operation(summary = "加班详情")
    @GetMapping("/overtime/{id}")
    public BaseResponse<OvertimeDO> getOvertime(@PathVariable @NotBlank String id) {
        return BaseResponse.ok(attendanceService.getOvertime(id));
    }

    // ============== 请假 ==============

    /**
     * 提交请假申请
     *
     * @param dto 请假申请参数
     * @return 统一响应结果，包含请假记录 ID
     */
    @Operation(summary = "提交请假申请")
    @AuthApiPermission(apiCodes = "attendance:leave:create")
    @OperationLog(module = "考勤", action = "提交请假", bizType = "LEAVE")
    @Idempotent(key = "attendance:submitLeave", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/leave")
    public BaseResponse<String> submitLeave(@Valid @RequestBody LeaveCreateDTO dto) {
        return BaseResponse.ok(attendanceService.submitLeave(dto));
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
    @AuthApiPermission(apiCodes = "attendance:leave:approve")
    @OperationLog(module = "考勤", action = "审批请假", bizType = "LEAVE")
    @Idempotent(key = "attendance:approveLeave", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/leave/{id}/approve")
    public BaseResponse<Void> approveLeave(
            @PathVariable @NotBlank String id,
            @RequestParam String action,
            @RequestHeader(value = "X-User-Id", required = false) String approverId,
            @RequestHeader(value = "X-Username", required = false) String approverName,
            @RequestParam(required = false) String remark) {
        attendanceService.approveLeave(id, action, approverId, approverName, remark);
        return BaseResponse.ok();
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
    @AuthApiPermission(apiCodes = "attendance:leave:list")
    @GetMapping("/leave/page")
    public BaseResponse<Page<LeaveDO>> pageLeave(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return BaseResponse.ok(attendanceService.pageLeave(employeeId, approvalStatus, page, size));
    }

    /**
     * 查询请假详情
     *
     * @param id 请假记录 ID
     * @return 统一响应结果，包含请假记录
     */
    @Operation(summary = "请假详情")
    @GetMapping("/leave/{id}")
    public BaseResponse<LeaveDO> getLeave(@PathVariable @NotBlank String id) {
        return BaseResponse.ok(attendanceService.getLeave(id));
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
    public BaseResponse<List<LeaveDO>> listApprovedLeaves(
            @RequestParam @NotBlank String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return BaseResponse.ok(attendanceService.listApprovedLeaves(employeeId, startDate, endDate));
    }
}
