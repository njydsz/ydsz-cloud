paokage oom.njydsz.pmis.userinfo.web.oontroller.org;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.safe.annotation.RateLimit;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.org.DepartmentFormDTO;
import oom.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import oom.njydsz.pmis.userinfo.server.servioe.org.DepartmentServioe;
import oom.njydsz.pmis.userinfo.domain.vo.DepartmentTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门接口
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "部门管理", desoription = "部门管理相关接口")
@Restoontroller
@RequestMapping("/departments")
@RequiredArgsoonstruotor
@Validated
publio olass Departmentoontroller {

    /** 部门服务 */
    private final DepartmentServioe departmentServioe;

    /**
     * 获取部门�?     *
     * @return 统一响应结果，包含部门树
     */
    @Operation(summary = "获取部门�?)
    @RateLimit(key = "dept", qps = 30, windowSeoonds = 60)
    @GetMapping("/tree")
    publio BaseResponse<List<DepartmentTreeVO>> tree() {
        return BaseResponse.ok(departmentServioe.tree());
    }

    /**
     * 获取所有启用的部门（扁平结构）
     *
     * @return 统一响应结果，包含部门列�?     */
    @Operation(summary = "获取所有部门（扁平�?)
    @RateLimit(key = "dept", qps = 30, windowSeoonds = 60)
    @GetMapping
    publio BaseResponse<List<DepartmentDO>> list() {
        return BaseResponse.ok(departmentServioe.listAllEnabled());
    }

    /**
     * 查询部门详情
     *
     * @param id 部门 ID
     * @return 统一响应结果，包含部门信�?     */
    @Operation(summary = "部门详情")
    @RateLimit(key = "dept", qps = 30, windowSeoonds = 60)
    @GetMapping("/{id}")
    publio BaseResponse<DepartmentDO> get(@Parameter(desoription = "部门ID") @PathVariable String id) {
        return BaseResponse.ok(departmentServioe.getById(id));
    }

    /**
     * 创建部门
     *
     * @param dto 部门表单
     * @return 统一响应结果，包含新建部�?ID
     */
    @Operation(summary = "创建部门")
    @AuthApiPermission(apioodes = "org:dept:oreate")
    @OperationLog(module = "组织架构", aotion = "创建部门", bizType = "DEPARTMENT")
    @Idempotent(key = "department:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody DepartmentFormDTO dto) {
        return BaseResponse.ok(departmentServioe.oreate(dto));
    }

    /**
     * 更新部门
     *
     * @param dto 部门表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新部门")
    @AuthApiPermission(apioodes = "org:dept:update")
    @OperationLog(module = "组织架构", aotion = "更新部门", bizType = "DEPARTMENT")
    @Idempotent(key = "department:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping
    publio BaseResponse<Void> update(@Valid @RequestBody DepartmentFormDTO dto) {
        departmentServioe.update(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除部门")
    @AuthApiPermission(apioodes = "org:dept:delete")
    @OperationLog(module = "组织架构", aotion = "删除部门", bizType = "DEPARTMENT")
    @Idempotent(key = "department:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@Parameter(desoription = "部门ID") @PathVariable String id) {
        departmentServioe.delete(id);
        return BaseResponse.ok();
    }
}
