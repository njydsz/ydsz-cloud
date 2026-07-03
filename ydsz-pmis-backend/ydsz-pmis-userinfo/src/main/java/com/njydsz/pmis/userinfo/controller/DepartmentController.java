package com.njydsz.pmis.userinfo.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.DepartmentFormDTO;
import com.njydsz.pmis.userinfo.entity.DepartmentDO;
import com.njydsz.pmis.userinfo.service.DepartmentService;
import com.njydsz.pmis.userinfo.vo.DepartmentTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "组织架构-部门")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    /** 部门服务 */
    private final DepartmentService departmentService;

    /**
     * 获取部门树
     *
     * @return 统一响应结果，包含部门树
     */
    @Operation(summary = "获取部门树")
    @GetMapping("/tree")
    public Result<List<DepartmentTreeVO>> tree() {
        return Result.ok(departmentService.tree());
    }

    /**
     * 获取所有启用的部门（扁平结构）
     *
     * @return 统一响应结果，包含部门列表
     */
    @Operation(summary = "获取所有部门（扁平）")
    @GetMapping
    public Result<List<DepartmentDO>> list() {
        return Result.ok(departmentService.listAllEnabled());
    }

    /**
     * 查询部门详情
     *
     * @param id 部门 ID
     * @return 统一响应结果，包含部门信息
     */
    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    public Result<DepartmentDO> get(@PathVariable Long id) {
        return Result.ok(departmentService.getById(id));
    }

    /**
     * 创建部门
     *
     * @param dto 部门表单
     * @return 统一响应结果，包含新建部门 ID
     */
    @Operation(summary = "创建部门")
    @PrePermission("org:dept:create")
    @OperationLog(module = "组织架构", action = "创建部门", bizType = "DEPARTMENT")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DepartmentFormDTO dto) {
        return Result.ok(departmentService.create(dto));
    }

    /**
     * 更新部门
     *
     * @param dto 部门表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新部门")
    @PrePermission("org:dept:update")
    @OperationLog(module = "组织架构", action = "更新部门", bizType = "DEPARTMENT")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody DepartmentFormDTO dto) {
        departmentService.update(dto);
        return Result.ok();
    }

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除部门")
    @PrePermission("org:dept:delete")
    @OperationLog(module = "组织架构", action = "删除部门", bizType = "DEPARTMENT")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.ok();
    }
}
