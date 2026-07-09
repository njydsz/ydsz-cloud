package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.RouteRuleUpsertDTO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.service.RouteRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @PrePermission(PermissionCodes.MESSAGE_ROUTE_RULE_CREATE)
    @PostMapping
    public Result<MsgRouteRuleDO> create(@Valid @RequestBody RouteRuleUpsertDTO dto) {
        return Result.ok(routeRuleService.create(dto));
    }

    @Operation(summary = "更新路由规则")
    @PrePermission(PermissionCodes.MESSAGE_ROUTE_RULE_UPDATE)
    @PutMapping("/{id}")
    public Result<MsgRouteRuleDO> update(@PathVariable String id, @Valid @RequestBody RouteRuleUpsertDTO dto) {
        return Result.ok(routeRuleService.update(id, dto));
    }

    @Operation(summary = "删除路由规则")
    @PrePermission(PermissionCodes.MESSAGE_ROUTE_RULE_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        routeRuleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "路由规则详情")
    @PrePermission(PermissionCodes.MESSAGE_ROUTE_RULE_VIEW)
    @GetMapping("/{id}")
    public Result<MsgRouteRuleDO> getById(@PathVariable String id) {
        return Result.ok(routeRuleService.getById(id));
    }

    @Operation(summary = "路由规则分页")
    @PrePermission(PermissionCodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/page")
    public Result<Page<MsgRouteRuleDO>> page(com.njydsz.pmis.common.entity.PageQuery query) {
        return Result.ok(routeRuleService.page(query));
    }

    @Operation(summary = "查询启用的路由规则")
    @PrePermission(PermissionCodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/enabled")
    public Result<List<MsgRouteRuleDO>> listEnabled() {
        return Result.ok(routeRuleService.listEnabled());
    }
}
