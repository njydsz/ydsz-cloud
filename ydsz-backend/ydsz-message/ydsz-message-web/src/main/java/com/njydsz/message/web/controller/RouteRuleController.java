package com.njydsz.message.web.controller.config;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.config.RouteRuleUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgRouteRuleDO;
import com.njydsz.message.server.service.config.RouteRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 路由规则 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "路由规则", description = "消息路由规则管理")
@RestController
@RequestMapping("/message/routeRule")
@RequiredArgsConstructor
public class RouteRuleController {

    /** 路由规则服务 */
    private final RouteRuleService routeRuleService;

    /**
     * 创建路由规则。
     *
     * @param dto 路由规则保存请求体
     * @return 统一响应结果，包含路由规则详情
     */
    @Operation(summary = "创建路由规则")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_CREATE)
    @Idempotent(key = "routeRule:create", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "路由规则", type = AuditType.CONFIG, action = AuditAction.CREATE, content = "'create'")
    @SentinelRateLimit(resource = "message.routerule.create", threshold = 50)
    @SentinelRateLimit(resource = "message.routerule.create", threshold = 50)
    @PostMapping
    public BaseResponse<MsgRouteRuleDO> create(@Valid @RequestBody RouteRuleUpsertDTO dto) {
        return BaseResponse.success(routeRuleService.create(dto));
    }

    /**
     * 更新路由规则。
     *
     * @param id  规则 ID
     * @param dto 路由规则保存请求体
     * @return 统一响应结果，包含更新后规则详情
     */
    @Operation(summary = "更新路由规则")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_UPDATE)
    @Idempotent(key = "routeRule:update", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "路由规则", type = AuditType.CONFIG, action = AuditAction.UPDATE, content = "'update'")
    @SentinelRateLimit(resource = "message.routerule.update", threshold = 50)
    @SentinelRateLimit(resource = "message.routerule.update", threshold = 50)
    @PutMapping("/{id}")
    public BaseResponse<MsgRouteRuleDO> update(@PathVariable String id, @Valid @RequestBody RouteRuleUpsertDTO dto) {
        return BaseResponse.success(routeRuleService.update(id, dto));
    }

    /**
     * 删除路由规则。
     *
     * @param id 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除路由规则")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_DELETE)
    @Idempotent(key = "routeRule:delete", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "路由规则", type = AuditType.CONFIG, action = AuditAction.DELETE, content = "'delete'")
    @SentinelRateLimit(resource = "message.routerule.delete", threshold = 50)
    @SentinelRateLimit(resource = "message.routerule.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        routeRuleService.delete(id);
        return BaseResponse.success();
    }

    /**
     * 查询路由规则详情。
     *
     * @param id 规则 ID
     * @return 统一响应结果，包含路由规则详情
     */
    @Operation(summary = "路由规则详情")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_VIEW)
    @GetMapping("/{id}")
    public BaseResponse<MsgRouteRuleDO> getById(@PathVariable String id) {
        return BaseResponse.success(routeRuleService.getById(id));
    }

    /**
     * 分页查询路由规则列表。
     *
     * @param query 分页查询参数
     * @return 统一响应结果，包含路由规则分页数据
     */
    @Operation(summary = "路由规则分页")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/page")
    public BaseResponse<Page<MsgRouteRuleDO>> page(PageQuery query) {
        return BaseResponse.success(routeRuleService.page(query));
    }

    /**
     * 查询全部启用的路由规则。
     *
     * @return 统一响应结果，包含启用的路由规则列表
     */
    @Operation(summary = "查询启用的路由规则")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/enabled")
    public BaseResponse<List<MsgRouteRuleDO>> listEnabled() {
        return BaseResponse.success(routeRuleService.listEnabled());
    }
}
