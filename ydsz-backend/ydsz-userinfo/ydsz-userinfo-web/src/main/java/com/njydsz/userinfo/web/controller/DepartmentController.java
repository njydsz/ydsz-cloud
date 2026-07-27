package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.server.service.DepartmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 部门 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/dept")
@RequiredArgsConstructor
@Tag(name = "部门管理", description = "部门 CRUD、树形结构查询")
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping("/list")
    @Operation(summary = "查询全部部门列表")
    public BaseResponse<List<DepartmentVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/tree")
    @Operation(summary = "查询部门树形结构")
    public BaseResponse<List<DepartmentTreeVO>> tree() {
        return BaseResponse.success(service.tree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询部门")
    public BaseResponse<DepartmentVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @SentinelRateLimit(resource = "userinfo.department.create", threshold = 50)
    @Audit(module = "部门管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建部门: ' + #dto.deptName")
    @Idempotent(key = "ydsz:userinfo:DepartmentController:create:lock", ttlSeconds = 5)
    @PostMapping
    @Operation(summary = "创建部门")
    public BaseResponse<String> create(@Valid @RequestBody DepartmentSaveDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @SentinelRateLimit(resource = "userinfo.department.update", threshold = 50)
    @Audit(module = "部门管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新部门: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:DepartmentController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Operation(summary = "更新部门")
    public BaseResponse<Boolean> update(@Valid @RequestBody DepartmentSaveDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @SentinelRateLimit(resource = "userinfo.department.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:DepartmentController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
