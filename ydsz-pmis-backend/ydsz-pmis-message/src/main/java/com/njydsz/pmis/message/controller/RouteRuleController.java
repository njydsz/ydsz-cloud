package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.RouteRuleUpsertDTO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.service.RouteRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 路由规则 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "路由规则", description = "消息路由规则管理")
@RestController
@RequestMapping("/message/route-rule")
@RequiredArgsConstructor
public class RouteRuleController {

    private final RouteRuleService routeRuleService;

    @Operation(summary = "创建路由规则")
    @PostMapping
    public Result<MsgRouteRuleDO> create(@RequestBody RouteRuleUpsertDTO dto) {
        // TODO 权限码
        return Result.ok(routeRuleService.create(dto));
    }

    @Operation(summary = "更新路由规则")
    @PutMapping("/{id}")
    public Result<MsgRouteRuleDO> update(@PathVariable String id, @RequestBody RouteRuleUpsertDTO dto) {
        // TODO 权限码
        return Result.ok(routeRuleService.update(id, dto));
    }

    @Operation(summary = "删除路由规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        // TODO 权限码
        routeRuleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "路由规则详情")
    @GetMapping("/{id}")
    public Result<MsgRouteRuleDO> getById(@PathVariable String id) {
        // TODO 权限码
        return Result.ok(routeRuleService.getById(id));
    }

    @Operation(summary = "路由规则分页")
    @GetMapping("/page")
    public Result<Page<MsgRouteRuleDO>> page(com.njydsz.pmis.common.entity.PageQuery query) {
        // TODO 权限码
        return Result.ok(routeRuleService.page(query));
    }

    @Operation(summary = "查询启用的路由规则")
    @GetMapping("/enabled")
    public Result<List<MsgRouteRuleDO>> listEnabled() {
        // TODO 权限码
        return Result.ok(routeRuleService.listEnabled());
    }
}
