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

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.entity.MenuDO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.server.service.MenuService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public BaseResponse<List<MenuDO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/tree")
    @Operation(summary = "查询菜单树形结构")
    public BaseResponse<List<MenuTreeVO>> tree() {
        return BaseResponse.success(service.tree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询菜单")
    public BaseResponse<MenuDO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建菜单")
    public BaseResponse<String> save(@RequestBody MenuDO entity) {
        return BaseResponse.success(service.save(entity));
    }

    @PutMapping
    @Operation(summary = "更新菜单")
    public BaseResponse<Boolean> update(@RequestBody MenuDO entity) {
        return BaseResponse.success(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
