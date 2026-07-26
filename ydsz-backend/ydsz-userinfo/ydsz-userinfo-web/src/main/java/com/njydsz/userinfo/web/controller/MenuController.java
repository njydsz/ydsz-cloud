package com.njydsz.userinfo.web.controller;

import java.util.List;

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
import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.server.service.MenuService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 菜单 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "菜单/权限 CRUD、树形结构查询")
public class MenuController {

    private final MenuService service;

    @GetMapping("/list")
    @Operation(summary = "查询全部菜单列表")
    public BaseResponse<List<MenuVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/tree")
    @Operation(summary = "查询菜单树形结构")
    public BaseResponse<List<MenuTreeVO>> tree() {
        return BaseResponse.success(service.tree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询菜单")
    public BaseResponse<MenuVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "菜单管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建菜单: ' + #dto.menuName")
    @Idempotent(key = "menu:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建菜单")
    public BaseResponse<String> create(@Valid @RequestBody MenuSaveDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @Audit(module = "菜单管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新菜单: ' + #dto.id")
    @Idempotent(key = "menu:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    @Operation(summary = "更新菜单")
    public BaseResponse<Boolean> update(@Valid @RequestBody MenuSaveDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @Audit(module = "菜单管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除菜单: ' + #id")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
