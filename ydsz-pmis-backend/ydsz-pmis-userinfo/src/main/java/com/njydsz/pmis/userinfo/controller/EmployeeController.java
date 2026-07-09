package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.EmployeeCreateDTO;
import com.njydsz.pmis.userinfo.dto.EmployeePageDTO;
import com.njydsz.pmis.userinfo.dto.EmployeeUpdateDTO;
import com.njydsz.pmis.userinfo.entity.EmployeeDO;
import com.njydsz.pmis.userinfo.service.EmployeeService;
import com.njydsz.pmis.userinfo.vo.EmployeeVO;
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
    @PrePermission("org:employee:create")
    @OperationLog(module = "员工管理", action = "创建员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody EmployeeCreateDTO dto) {
        return Result.ok(employeeService.create(dto));
    }

    /**
     * 更新员工
     *
     * @param id  员工 ID
     * @param dto 员工更新表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新员工")
    @PrePermission("org:employee:update")
    @OperationLog(module = "员工管理", action = "更新员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public Result<Void> update(@Parameter(description = "员工 ID") @PathVariable String id,
                               @Valid @RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除员工
     *
     * @param id 员工 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除员工")
    @PrePermission("org:employee:delete")
    @OperationLog(module = "员工管理", action = "删除员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "员工 ID") @PathVariable String id) {
        employeeService.delete(id);
        return Result.ok();
    }

    /**
     * 查询员工详情（含部门 / 岗位 / 职级名称装配）
     *
     * @param id 员工 ID
     * @return 统一响应结果，包含员工视图
     */
    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    public Result<EmployeeVO> get(@Parameter(description = "员工 ID") @PathVariable String id) {
        return Result.ok(employeeService.assemble(employeeService.getById(id)));
    }

    /**
     * 分页查询员工
     *
     * @param query 查询条件
     * @return 统一响应结果，包含员工视图分页
     */
    @Operation(summary = "分页查询员工")
    @GetMapping
    public Result<Page<EmployeeVO>> page(@Valid @ModelAttribute EmployeePageDTO query) {
        Page<EmployeeDO> doPage = employeeService.page(
                (int) query.getPage(),
                (int) query.getSize(),
                query.getKeyword(),
                query.getDepartmentId(),
                query.getEmployeeType(),
                query.getWorkStatus());
        Page<EmployeeVO> voPage = new Page<>(doPage.getCurrent(), doPage.getSize(), doPage.getTotal());
        voPage.setRecords(doPage.getRecords().stream().map(employeeService::assemble).toList());
        return Result.ok(voPage);
    }

    /**
     * 按部门查询员工列表
     *
     * @param departmentId 部门 ID
     * @return 统一响应结果，包含员工列表
     */
    @Operation(summary = "按部门查询员工")
    @GetMapping("/by-department/{departmentId}")
    public Result<List<EmployeeDO>> listByDepartment(
            @Parameter(description = "部门 ID") @PathVariable String departmentId) {
        return Result.ok(employeeService.listByDepartment(departmentId));
    }
}
