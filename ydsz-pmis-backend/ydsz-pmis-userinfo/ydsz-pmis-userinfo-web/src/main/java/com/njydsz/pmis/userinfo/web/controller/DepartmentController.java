package com.njydsz.pmis.userinfo.web.controller.org;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.safe.annotation.RateLimit;
import com.njydsz.pmis.userinfo.domain.dto.org.DepartmentFormDTO;
import com.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import com.njydsz.pmis.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.pmis.userinfo.server.service.org.DepartmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 部门接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "部门管理", description = "部门管理相关接口")
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Validated
public class DepartmentController {

    /** 部门服务 */
    private final DepartmentService departmentService;

    /**
     * 获取部门树
     *
     * @return 统一响应结果，包含部门树
     */
    @Operation(summary = "获取部门树")
    @RateLimit(key = "dept", qps = 30, windowSeconds = 60)
    @GetMapping("/tree")
    public BaseResponse<List<DepartmentTreeVO>> tree() {
        return BaseResponse.ok(departmentService.tree());
    }

    /**
     * 获取所有启用的部门（扁平结构）
     *
     * @return 统一响应结果，包含部门列表
     */
    @Operation(summary = "获取所有部门（扁平）")
    @RateLimit(key = "dept", qps = 30, windowSeconds = 60)
    @GetMapping
    public BaseResponse<List<DepartmentDO>> list() {
        return BaseResponse.ok(departmentService.listAllEnabled());
    }

    /**
     * 查询部门详情
     *
     * @param id 部门 ID
     * @return 统一响应结果，包含部门信息
     */
    @Operation(summary = "部门详情")
    @RateLimit(key = "dept", qps = 30, windowSeconds = 60)
    @GetMapping("/{id}")
    public BaseResponse<DepartmentDO> get(@Parameter(description = "部门ID") @PathVariable String id) {
        return BaseResponse.ok(departmentService.getById(id));
    }

    /**
     * 创建部门
     *
     * @param dto 部门表单
     * @return 统一响应结果，包含新建部门 ID
     */
    @Operation(summary = "创建部门")
    @AuthApiPermission(apiCodes = "org:dept:create")
    @OperationLog(module = "组织架构", action = "创建部门", bizType = "DEPARTMENT")
    @Idempotent(key = "department:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody DepartmentFormDTO dto) {
        return BaseResponse.ok(departmentService.create(dto));
    }

    /**
     * 更新部门
     *
     * @param dto 部门表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新部门")
    @AuthApiPermission(apiCodes = "org:dept:update")
    @OperationLog(module = "组织架构", action = "更新部门", bizType = "DEPARTMENT")
    @Idempotent(key = "department:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody DepartmentFormDTO dto) {
        departmentService.update(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除部门")
    @AuthApiPermission(apiCodes = "org:dept:delete")
    @OperationLog(module = "组织架构", action = "删除部门", bizType = "DEPARTMENT")
    @Idempotent(key = "department:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "部门ID") @PathVariable String id) {
        departmentService.delete(id);
        return BaseResponse.ok();
    }
}
