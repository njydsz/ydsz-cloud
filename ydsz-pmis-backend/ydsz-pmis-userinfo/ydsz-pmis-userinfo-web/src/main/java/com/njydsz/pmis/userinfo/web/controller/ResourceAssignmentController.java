package com.njydsz.pmis.userinfo.web.controller.resource;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.userinfo.domain.dto.resource.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.resource.ResourceAssignmentDO;
import com.njydsz.pmis.userinfo.server.service.resource.ResourceAssignmentService;
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
@RequestMapping("/resourceAssignments")
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
    @AuthApiPermission(apiCodes = "resource:assign:act")
    @OperationLog(module = "资源分配", action = "分配动作", bizType = "RESOURCE_ASSIGN")
    @Idempotent(key = "resourceAssignment:act", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/act")
    public BaseResponse<String> act(@Valid @RequestBody ResourceAssignmentCreateDTO dto) {
        return BaseResponse.ok(assignmentService.act(dto));
    }

    /**
     * 查询分配详情
     *
     * @param id 分配记录 ID
     * @return 统一响应结果，包含分配记录
     */
    @Operation(summary = "分配详情")
    @GetMapping("/{id}")
    public BaseResponse<ResourceAssignmentDO> get(@PathVariable String id) {
        return BaseResponse.ok(assignmentService.getById(id));
    }

    /**
     * 按员工查询分配记录
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含分配记录列表
     */
    @Operation(summary = "按员工查询")
    @GetMapping("/byEmployee/{employeeId}")
    public BaseResponse<List<ResourceAssignmentDO>> listByEmployee(@PathVariable String employeeId) {
        return BaseResponse.ok(assignmentService.listByEmployee(employeeId));
    }

    /**
     * 按项目查询分配记录
     *
     * @param initiationId 立项 ID
     * @return 统一响应结果，包含分配记录列表
     */
    @Operation(summary = "按项目查询")
    @GetMapping("/byInitiation/{initiationId}")
    public BaseResponse<List<ResourceAssignmentDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(assignmentService.listByInitiation(initiationId));
    }

    /**
     * 查询员工活跃项目数
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含活跃项目数
     */
    @Operation(summary = "员工活跃项目数")
    @GetMapping("/activeCount/{employeeId}")
    public BaseResponse<Integer> activeCount(@PathVariable String employeeId) {
        return BaseResponse.ok(assignmentService.activeCount(employeeId));
    }

    /**
     * 查询员工利用率
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含利用率统计
     */
    @Operation(summary = "员工利用率")
    @GetMapping("/utilization/{employeeId}")
    public BaseResponse<Map<String, Object>> utilization(@PathVariable String employeeId) {
        return BaseResponse.ok(assignmentService.utilization(employeeId));
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
    public BaseResponse<Page<ResourceAssignmentDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(assignmentService.page(page, size, employeeId, initiationId, status));
    }
}
