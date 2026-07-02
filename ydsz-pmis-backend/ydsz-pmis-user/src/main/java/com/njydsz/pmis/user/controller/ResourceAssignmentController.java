package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.user.entity.ResourceAssignmentDO;
import com.njydsz.pmis.user.service.ResourceAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/resource-assignments")
@RequiredArgsConstructor
public class ResourceAssignmentController {

    private final ResourceAssignmentService assignmentService;

    @Operation(summary = "分配动作（RESERVE/START/TRANSFER/RELEASE/CANCEL）")
    @PrePermission("resource:assign:act")
    @OperationLog(module = "资源分配", action = "分配动作", bizType = "RESOURCE_ASSIGN")
    @PostMapping("/act")
    public Result<Long> act(@Valid @RequestBody ResourceAssignmentCreateDTO dto) {
        return Result.ok(assignmentService.act(dto));
    }

    @Operation(summary = "分配详情")
    @GetMapping("/{id}")
    public Result<ResourceAssignmentDO> get(@PathVariable Long id) {
        return Result.ok(assignmentService.getById(id));
    }

    @Operation(summary = "按员工查询")
    @GetMapping("/by-employee/{employeeId}")
    public Result<List<ResourceAssignmentDO>> listByEmployee(@PathVariable Long employeeId) {
        return Result.ok(assignmentService.listByEmployee(employeeId));
    }

    @Operation(summary = "按项目查询")
    @GetMapping("/by-initiation/{initiationId}")
    public Result<List<ResourceAssignmentDO>> listByInitiation(@PathVariable Long initiationId) {
        return Result.ok(assignmentService.listByInitiation(initiationId));
    }

    @Operation(summary = "员工活跃项目数")
    @GetMapping("/active-count/{employeeId}")
    public Result<Integer> activeCount(@PathVariable Long employeeId) {
        return Result.ok(assignmentService.activeCount(employeeId));
    }

    @Operation(summary = "员工利用率")
    @GetMapping("/utilization/{employeeId}")
    public Result<Map<String, Object>> utilization(@PathVariable Long employeeId) {
        return Result.ok(assignmentService.utilization(employeeId));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<Page<ResourceAssignmentDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String status) {
        return Result.ok(assignmentService.page(page, size, employeeId, initiationId, status));
    }
}
