package com.njydsz.pmis.userinfo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.userinfo.entity.ResourceAssignmentDO;
import com.njydsz.pmis.userinfo.service.ResourceAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 资源分配 Controller
 *
 * <p>覆盖预占/入场/调岗/离场 业务动作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "资源分配管理")
@RestController
@RequestMapping("/resource-assignments")
@RequiredArgsConstructor
@Validated
public class ResourceAssignmentController {

    /** 资源分配服务 */
    private final ResourceAssignmentService assignmentService;

    /**
     * 资源分配动作（RESERVE/START/TRANSFER/RELEASE/CANCEL）
     *
     * @param dto 分配动作参数
     * @return 统一响应结果，包含分配记录 ID
     */
    @Operation(summary = "分配动作（RESERVE/START/TRANSFER/RELEASE/CANCEL）")
    @PrePermission("resource:assign:act")
    @OperationLog(module = "资源分配", action = "分配动作", bizType = "RESOURCE_ASSIGN")
    @PostMapping("/act")
    public Result<Long> act(@Valid @RequestBody ResourceAssignmentCreateDTO dto) {
        return Result.ok(assignmentService.act(dto));
    }

    /**
     * 查询分配详情
     *
     * @param id 分配记录 ID
     * @return 统一响应结果，包含分配记录
     */
    @Operation(summary = "分配详情")
    @GetMapping("/{id}")
    public Result<ResourceAssignmentDO> get(@PathVariable @Min(1) Long id) {
        return Result.ok(assignmentService.getById(id));
    }

    /**
     * 按员工查询分配记录
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含分配记录列表
     */
    @Operation(summary = "按员工查询")
    @GetMapping("/by-employee/{employeeId}")
    public Result<List<ResourceAssignmentDO>> listByEmployee(@PathVariable @Min(1) Long employeeId) {
        return Result.ok(assignmentService.listByEmployee(employeeId));
    }

    /**
     * 按项目查询分配记录
     *
     * @param initiationId 立项 ID
     * @return 统一响应结果，包含分配记录列表
     */
    @Operation(summary = "按项目查询")
    @GetMapping("/by-initiation/{initiationId}")
    public Result<List<ResourceAssignmentDO>> listByInitiation(@PathVariable @Min(1) Long initiationId) {
        return Result.ok(assignmentService.listByInitiation(initiationId));
    }

    /**
     * 查询员工活跃项目数
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含活跃项目数
     */
    @Operation(summary = "员工活跃项目数")
    @GetMapping("/active-count/{employeeId}")
    public Result<Integer> activeCount(@PathVariable @Min(1) Long employeeId) {
        return Result.ok(assignmentService.activeCount(employeeId));
    }

    /**
     * 查询员工利用率
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含利用率统计
     */
    @Operation(summary = "员工利用率")
    @GetMapping("/utilization/{employeeId}")
    public Result<Map<String, Object>> utilization(@PathVariable @Min(1) Long employeeId) {
        return Result.ok(assignmentService.utilization(employeeId));
    }

    /**
     * 分页查询分配记录
     *
     * @param page         页码
     * @param size         每页大小
     * @param employeeId   员工 ID（可选）
     * @param initiationId 立项 ID（可选）
     * @param status       状态（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<Page<ResourceAssignmentDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String status) {
        return Result.ok(assignmentService.page(page, size, employeeId, initiationId, status));
    }
}
