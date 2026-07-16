package com.njydsz.pmis.project.web.controller.execution;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.project.domain.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.project.domain.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.project.domain.entity.TimeEntryDO;
import com.njydsz.pmis.project.server.service.TimeEntryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 工时管理 Controller
 *
 * <p>负责工时录入、审批、聚合查询及跨项目冲突检测。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "工时管理")
@RestController
@RequestMapping("/api/project/execution/timeEntry")
@RequiredArgsConstructor
@Validated
public class TimeEntryController {

    /** 工时填报服务 */
    private final TimeEntryService service;

    /**
     * 录入工时
     *
     * @param dto 工时录入参数
     * @return 新建工时记录 ID
     */
    @Operation(summary = "录入工时")
    @AuthApiPermission(apiCodes = "execution:time:create")
    @Idempotent(key = "timeEntry:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody TimeEntryCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 提交工时审批
     *
     * @param id 工时记录 ID
     * @return 空结果
     */
    @Operation(summary = "提交工时审批")
    @AuthApiPermission(apiCodes = "execution:time:approve")
    @Idempotent(key = "timeEntry:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/submit")
    public BaseResponse<Void> submit(@PathVariable String id) {
        service.submit(id);
        return BaseResponse.ok();
    }

    /**
     * 审批工时
     *
     * @param dto 工时审批参数
     * @return 空结果
     */
    @Operation(summary = "审批工时")
    @AuthApiPermission(apiCodes = "execution:time:approve")
    @Idempotent(key = "timeEntry:approve", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/approve")
    public BaseResponse<Void> approve(@Valid @RequestBody TimeEntryApprovalDTO dto) {
        service.approve(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除工时
     *
     * @param id 工时记录 ID
     * @return 空结果
     */
    @Operation(summary = "删除工时")
    @AuthApiPermission(apiCodes = "execution:time:delete")
    @Idempotent(key = "timeEntry:delete", ttlSeconds = 5, message = "请勿重复提交")
    @OperationLog(module = "工时管理", action = "删除工时", bizType = "TIME_ENTRY")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询工时详情
     *
     * @param id 工时记录 ID
     * @return 工时实体
     */
    @Operation(summary = "工时详情")
    @AuthApiPermission(apiCodes = "execution:time:list")
    @GetMapping("/{id}")
    public BaseResponse<TimeEntryDO> get(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询工时
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param employeeId   员工 ID
     * @param initiationId 项目立项 ID
     * @param taskId       任务 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apiCodes = "execution:time:list")
    @GetMapping("/page")
    public BaseResponse<Page<TimeEntryDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(service.page(page, size, keyword, status, employeeId, initiationId, taskId, from, to));
    }

    /**
     * 按人员+职级聚合项目工时
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 聚合结果列表
     */
    @Operation(summary = "项目工时按人员+职级聚合")
    @AuthApiPermission(apiCodes = "execution:time:list")
    @GetMapping("/aggregate/byEmployeeLevel")
    public BaseResponse<List<Map<String, Object>>> aggregateByEmployeeLevel(
            @RequestParam String initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return BaseResponse.ok(service.aggregateHoursByEmployeeAndLevel(initiationId, from, to));
    }

    /**
     * 跨项目工时冲突检测
     *
     * @param employeeId 员工 ID
     * @param entryDate  工时日期
     * @return 冲突列表
     */
    @Operation(summary = "跨项目冲突检测")
    @AuthApiPermission(apiCodes = "execution:time:list")
    @GetMapping("/conflict")
    public BaseResponse<List<Map<String, Object>>> detectCrossProject(
            @RequestParam String employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate) {
        return BaseResponse.ok(service.detectCrossProject(employeeId, entryDate));
    }

    /**
     * 工时异常统计（按项目 + 月份）
     *
     * <p>聚合指定项目在指定月份的工时异常情况，供 Agent 工具 / 周报月报场景调用。
     *
     * @param initiationId 项目立项 ID
     * @param month        月份（yyyy-MM），为空时取当前月
     * @return 异常统计 Map（overtimeCount/missingCount/abnormalCount/totalHours）
     */
    @Operation(summary = "工时异常统计")
    @AuthApiPermission(apiCodes = "execution:time:list")
    @GetMapping("/abnormalStat")
    public BaseResponse<Map<String, Object>> abnormalStat(
            @RequestParam String initiationId,
            @RequestParam(required = false) String month) {
        return BaseResponse.ok(service.abnormalStat(initiationId, month));
    }
}
