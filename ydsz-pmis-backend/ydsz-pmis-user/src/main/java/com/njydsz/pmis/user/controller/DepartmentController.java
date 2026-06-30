package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.DepartmentFormDTO;
import com.njydsz.pmis.user.entity.DepartmentDO;
import com.njydsz.pmis.user.service.DepartmentService;
import com.njydsz.pmis.user.vo.DepartmentTreeVO;
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

    private final DepartmentService departmentService;

    @Operation(summary = "获取部门树")
    @GetMapping("/tree")
    public R<List<DepartmentTreeVO>> tree() {
        return R.ok(departmentService.tree());
    }

    @Operation(summary = "获取所有部门（扁平）")
    @GetMapping
    public R<List<DepartmentDO>> list() {
        return R.ok(departmentService.listAllEnabled());
    }

    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    public R<DepartmentDO> get(@PathVariable Long id) {
        return R.ok(departmentService.getById(id));
    }

    @Operation(summary = "创建部门")
    @PrePermission("org:dept:create")
    @OperationLog(module = "组织架构", action = "创建部门", bizType = "DEPARTMENT")
    @PostMapping
    public R<Long> create(@Valid @RequestBody DepartmentFormDTO dto) {
        return R.ok(departmentService.create(dto));
    }

    @Operation(summary = "更新部门")
    @PrePermission("org:dept:update")
    @OperationLog(module = "组织架构", action = "更新部门", bizType = "DEPARTMENT")
    @PutMapping
    public R<Void> update(@Valid @RequestBody DepartmentFormDTO dto) {
        departmentService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除部门")
    @PrePermission("org:dept:delete")
    @OperationLog(module = "组织架构", action = "删除部门", bizType = "DEPARTMENT")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return R.ok();
    }
}
