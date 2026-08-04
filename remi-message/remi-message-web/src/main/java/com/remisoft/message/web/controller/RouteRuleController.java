package com.remisoft.message.web.controller.config;

import java.util.List;

import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
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
import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.domain.query.PageQuery;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.message.domain.converter.MessageConverter;
import com.remisoft.message.domain.dto.config.RouteRuleUpsertDTO;
import com.remisoft.message.domain.entity.config.MsgRouteRule;
import com.remisoft.message.domain.vo.MsgRouteRuleVO;
import com.remisoft.message.server.service.config.RouteRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;

/**
 * 路由规则 Controller。
 *
 * <p>提供<b>消息渠道路由规则</b>的 HTTP API，控制「什么类型的消息走什么渠道」的映射关系。
 * 路由规则按 (消息类型 / 业务类型 / 用户属性) 多维度决策，命中后选取对应渠道发送。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/route/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@code POST /} 创建 / {@code PUT /{id}} 编辑 / {@code DELETE /{id}} 删除</li>
 *   <li><b>分页查询</b>：{@code GET /page} 按渠道 / 消息类型 / 状态多维过滤</li>
 *   <li><b>规则启用</b>：{@code POST /{id}/enable} / {@code POST /{id}/disable}</li>
 *   <li><b>规则优先级</b>：通过 {@code priority} 字段控制匹配顺序（数字越小越先匹配）</li>
 * </ul>
 *
 * <p><b>路由决策：</b>当 {@code MessageService.send} 收到发送请求时，按以下顺序匹配路由规则：
 * <ol>
 *   <li>按 {@code (msgType, businessType, tenantId, priority)} 复合索引查匹配规则</li>
 *   <li>按优先级从低到高依次评估规则条件（{@code conditionExpression}）</li>
 *   <li>首条命中的规则返回渠道 ID，未命中则使用默认渠道（{@code DEFAULT_CHANNEL}）</li>
 * </ol>
 *
 * <p><b>多渠道支持：</b>SMS / EMAIL / IN_APP / DINGTALK / FEISHU / WECOM / WEBSOCKET 等。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重</li>
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_ROUTE_RULE_MANAGE} 等权限码</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see com.remisoft.message.server.service.config.RouteRuleService 路由规则服务
 * @see com.remisoft.message.domain.entity.config.MsgRouteRule 路由规则实体
 */
@Tag(name = "路由规则", description = "消息路由规则管理")
@RestController
@RequestMapping("/api/v1/message/routeRule")
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
    @Idempotent(key = "remi:message:RouteRuleController:create:lock", ttlSeconds = 5)
    @Audit(module = "路由规则", type = AuditType.CONFIG, action = AuditAction.CREATE, content = "'create'")
    @RateLimit(resource = "message.routerule.create", threshold = 50)
    @PostMapping
    public BaseResponse<MsgRouteRuleVO> create(@Valid @RequestBody RouteRuleUpsertDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(routeRuleService.create(dto)));
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
    @Idempotent(key = "remi:message:RouteRuleController:update:lock", ttlSeconds = 5)
    @Audit(module = "路由规则", type = AuditType.CONFIG, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "message.routerule.update", threshold = 50)
    @PutMapping("/{id}")
    public BaseResponse<MsgRouteRuleVO> update(@PathVariable String id, @Valid @RequestBody RouteRuleUpsertDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(routeRuleService.update(id, dto)));
    }

    /**
     * 删除路由规则。
     *
     * @param id 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除路由规则")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_DELETE)
    @Idempotent(key = "remi:message:RouteRuleController:delete:lock", ttlSeconds = 5)
    @Audit(module = "路由规则", type = AuditType.CONFIG, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "message.routerule.delete", threshold = 50)
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
    public BaseResponse<MsgRouteRuleVO> getById(@PathVariable String id) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(routeRuleService.getById(id)));
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
    public BaseResponse<Page<MsgRouteRuleVO>> page(PageQuery query) {
        Page<MsgRouteRule> page = routeRuleService.page(query);
        Page<MsgRouteRuleVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(MessageConverter.INSTANT.routeRuleListToVO(page.getRecords()));
        return BaseResponse.success(voPage);
    }

    /**
     * 查询全部启用的路由规则。
     *
     * @return 统一响应结果，包含启用的路由规则列表
     */
    @Operation(summary = "查询启用的路由规则")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/enabled")
    public BaseResponse<List<MsgRouteRuleVO>> listEnabled() {
        return BaseResponse.success(MessageConverter.INSTANT.routeRuleListToVO(routeRuleService.listEnabled()));
    }
}
