package com.njydsz.pmis.userinfo.web.controller.user;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.userinfo.domain.dto.user.EmployeeCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.user.EmployeePageDTO;
import com.njydsz.pmis.userinfo.domain.dto.user.EmployeeUpdateDTO;
import com.njydsz.pmis.userinfo.domain.entity.user.EmployeeDO;
import com.njydsz.pmis.userinfo.server.service.user.EmployeeService;
import com.njydsz.pmis.userinfo.domain.vo.EmployeeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "员工管理")
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@Validated
public class EmployeeController {

    /** 员工服务 */
    private final EmployeeService employeeService;

    /**
     * 创建员工
     *
     * @param dto 员工创建表单
     * @return 统一响应结果，包含新建员工 ID
     */
    @Operation(summary = "创建员工")
    @AuthApiPermission(apiCodes = "org:employee:create")
    @OperationLog(module = "员工管理", action = "创建员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody EmployeeCreateDTO dto) {
        return BaseResponse.ok(employeeService.create(dto));
    }

    /**
     * 更新员工
     *
     * @param id  员工 ID
     * @param dto 员工更新表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新员工")
    @AuthApiPermission(apiCodes = "org:employee:update")
    @OperationLog(module = "员工管理", action = "更新员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@Parameter(description = "员工 ID") @PathVariable String id,
                               @Valid @RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除员工
     *
     * @param id 员工 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除员工")
    @AuthApiPermission(apiCodes = "org:employee:delete")
    @OperationLog(module = "员工管理", action = "删除员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "员工 ID") @PathVariable String id) {
        employeeService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询员工详情（含部门 / 岗位 / 职级名称装配）
     *
     * @param id 员工 ID
     * @return 统一响应结果，包含员工视图
     */
    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    public BaseResponse<EmployeeVO> get(@Parameter(description = "员工 ID") @PathVariable String id) {
        return BaseResponse.ok(employeeService.assemble(employeeService.getById(id)));
    }

    /**
     * 分页查询员工
     *
     * @param query 查询条件
     * @return 统一响应结果，包含员工视图分页
     */
    @Operation(summary = "分页查询员工")
    @GetMapping
    public BaseResponse<Page<EmployeeVO>> page(@Valid @ModelAttribute EmployeePageDTO query) {
        Page<EmployeeDO> doPage = employeeService.page(
                (int) query.getPage(),
                (int) query.getSize(),
                query.getKeyword(),
                query.getDepartmentId(),
                query.getEmployeeType(),
                query.getWorkStatus());
        Page<EmployeeVO> voPage = new Page<>(doPage.getCurrent(), doPage.getSize(), doPage.getTotal());
        voPage.setRecords(doPage.getRecords().stream().map(employeeService::assemble).toList());
        return BaseResponse.ok(voPage);
    }

    /**
     * 按部门查询员工列表
     *
     * @param departmentId 部门 ID
     * @return 统一响应结果，包含员工列表
     */
    @Operation(summary = "按部门查询员工")
    @GetMapping("/byDepartment/{departmentId}")
    public BaseResponse<List<EmployeeDO>> listByDepartment(
            @Parameter(description = "部门 ID") @PathVariable String departmentId) {
        return BaseResponse.ok(employeeService.listByDepartment(departmentId));
    }
}
