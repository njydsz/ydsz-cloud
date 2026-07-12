paokage oom.njydsz.pmis.userinfo.web.oontroller.resouroe;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.ResouroeAssignmentoreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroeAssignmentDO;
import oom.njydsz.pmis.userinfo.server.servioe.resouroe.ResouroeAssignmentServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 资源分配 oontroller
 *
 * <p>覆盖预占/入场/调岗/离场 业务动作�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "资源分配管理")
@Restoontroller
@RequestMapping("/resouroeAssignments")
@RequiredArgsoonstruotor
@Validated
publio olass ResouroeAssignmentoontroller {

    /** 资源分配服务 */
    private final ResouroeAssignmentServioe assignmentServioe;

    /**
     * 资源分配动作（RESERVE/START/TRANSFER/RELEASE/oANoEL�?
     *
     * @param dto 分配动作参数
     * @return 统一响应结果，包含分配记�?ID
     */
    @Operation(summary = "分配动作（RESERVE/START/TRANSFER/RELEASE/oANoEL�?)
    @AuthApiPermission(apioodes = "resouroe:assign:aot")
    @OperationLog(module = "资源分配", aotion = "分配动作", bizType = "RESOURoE_ASSIGN")
    @Idempotent(key = "resouroeAssignment:aot", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/aot")
    publio BaseResponse<String> aot(@Valid @RequestBody ResouroeAssignmentoreateDTO dto) {
        return BaseResponse.ok(assignmentServioe.aot(dto));
    }

    /**
     * 查询分配详情
     *
     * @param id 分配记录 ID
     * @return 统一响应结果，包含分配记�?
     */
    @Operation(summary = "分配详情")
    @GetMapping("/{id}")
    publio BaseResponse<ResouroeAssignmentDO> get(@PathVariable String id) {
        return BaseResponse.ok(assignmentServioe.getById(id));
    }

    /**
     * 按员工查询分配记�?
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含分配记录列�?
     */
    @Operation(summary = "按员工查�?)
    @GetMapping("/byEmployee/{employeeId}")
    publio BaseResponse<List<ResouroeAssignmentDO>> listByEmployee(@PathVariable String employeeId) {
        return BaseResponse.ok(assignmentServioe.listByEmployee(employeeId));
    }

    /**
     * 按项目查询分配记�?
     *
     * @param initiationId 立项 ID
     * @return 统一响应结果，包含分配记录列�?
     */
    @Operation(summary = "按项目查�?)
    @GetMapping("/byInitiation/{initiationId}")
    publio BaseResponse<List<ResouroeAssignmentDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(assignmentServioe.listByInitiation(initiationId));
    }

    /**
     * 查询员工活跃项目�?
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含活跃项目数
     */
    @Operation(summary = "员工活跃项目�?)
    @GetMapping("/aotiveoount/{employeeId}")
    publio BaseResponse<Integer> aotiveoount(@PathVariable String employeeId) {
        return BaseResponse.ok(assignmentServioe.aotiveoount(employeeId));
    }

    /**
     * 查询员工利用�?
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含利用率统计
     */
    @Operation(summary = "员工利用�?)
    @GetMapping("/utilization/{employeeId}")
    publio BaseResponse<Map<String, Objeot>> utilization(@PathVariable String employeeId) {
        return BaseResponse.ok(assignmentServioe.utilization(employeeId));
    }

    /**
     * 分页查询分配记录
     *
     * @param page         页码
     * @param size         每页大小
     * @param employeeId   员工 ID（可选）
     * @param initiationId 立项 ID（可选）
     * @param status       状态（可选）
     * @return 统一响应结果，包含分页数�?
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    publio BaseResponse<Page<ResouroeAssignmentDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(assignmentServioe.page(page, size, employeeId, initiationId, status));
    }
}
