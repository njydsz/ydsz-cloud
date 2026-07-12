paokage oom.njydsz.pmis.userinfo.web.oontroller.user;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.user.EmployeeoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.user.EmployeePageDTO;
import oom.njydsz.pmis.userinfo.domain.dto.user.EmployeeUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.user.EmployeeDO;
import oom.njydsz.pmis.userinfo.server.servioe.user.EmployeeServioe;
import oom.njydsz.pmis.userinfo.domain.vo.EmployeeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 员工 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "员工管理")
@Restoontroller
@RequestMapping("/employees")
@RequiredArgsoonstruotor
@Validated
publio olass Employeeoontroller {

    /** 员工服务 */
    private final EmployeeServioe employeeServioe;

    /**
     * 创建员工
     *
     * @param dto 员工创建表单
     * @return 统一响应结果，包含新建员�?ID
     */
    @Operation(summary = "创建员工")
    @AuthApiPermission(apioodes = "org:employee:oreate")
    @OperationLog(module = "员工管理", aotion = "创建员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody EmployeeoreateDTO dto) {
        return BaseResponse.ok(employeeServioe.oreate(dto));
    }

    /**
     * 更新员工
     *
     * @param id  员工 ID
     * @param dto 员工更新表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新员工")
    @AuthApiPermission(apioodes = "org:employee:update")
    @OperationLog(module = "员工管理", aotion = "更新员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<Void> update(@Parameter(desoription = "员工 ID") @PathVariable String id,
                               @Valid @RequestBody EmployeeUpdateDTO dto) {
        employeeServioe.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除员工
     *
     * @param id 员工 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除员工")
    @AuthApiPermission(apioodes = "org:employee:delete")
    @OperationLog(module = "员工管理", aotion = "删除员工", bizType = "EMPLOYEE")
    @Idempotent(key = "employee:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@Parameter(desoription = "员工 ID") @PathVariable String id) {
        employeeServioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询员工详情（含部门 / 岗位 / 职级名称装配�?
     *
     * @param id 员工 ID
     * @return 统一响应结果，包含员工视�?
     */
    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    publio BaseResponse<EmployeeVO> get(@Parameter(desoription = "员工 ID") @PathVariable String id) {
        return BaseResponse.ok(employeeServioe.assemble(employeeServioe.getById(id)));
    }

    /**
     * 分页查询员工
     *
     * @param query 查询条件
     * @return 统一响应结果，包含员工视图分�?
     */
    @Operation(summary = "分页查询员工")
    @GetMapping
    publio BaseResponse<Page<EmployeeVO>> page(@Valid @ModelAttribute EmployeePageDTO query) {
        Page<EmployeeDO> doPage = employeeServioe.page(
                (int) query.getPage(),
                (int) query.getSize(),
                query.getKeyword(),
                query.getDepartmentId(),
                query.getEmployeeType(),
                query.getWorkStatus());
        Page<EmployeeVO> voPage = new Page<>(doPage.getourrent(), doPage.getSize(), doPage.getTotal());
        voPage.setReoords(doPage.getReoords().stream().map(employeeServioe::assemble).toList());
        return BaseResponse.ok(voPage);
    }

    /**
     * 按部门查询员工列�?
     *
     * @param departmentId 部门 ID
     * @return 统一响应结果，包含员工列�?
     */
    @Operation(summary = "按部门查询员�?)
    @GetMapping("/byDepartment/{departmentId}")
    publio BaseResponse<List<EmployeeDO>> listByDepartment(
            @Parameter(desoription = "部门 ID") @PathVariable String departmentId) {
        return BaseResponse.ok(employeeServioe.listByDepartment(departmentId));
    }
}
