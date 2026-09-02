package com.njydsz.message.web.controller.config;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.RouteRuleUpsertDTO;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.server.service.config.RouteRuleService;

/**
 * 路由规则 Controller。
 *
 * <p>提供<b>消息渠道路由规则</b>的 HTTP API，控制「什么类型的消息走什么渠道」的映射关系。 路由规则按 (消息类型 / 业务类型 / 用户属性)
 * 多维度决策，命中后选取对应渠道发送。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/route-rule/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@code POST /} 创建 / {@code PUT /{id}} 编辑 / {@code DELETE /{id}} 删除
 *   <li><b>分页查询</b>：{@code GET /page} 按渠道 / 消息类型 / 状态多维过滤
 *   <li><b>规则启用</b>：{@code POST /{id}/enable} / {@code POST /{id}/disable}
 *   <li><b>规则优先级</b>：通过 {@code priority} 字段控制匹配顺序（数字越小越先匹配）
 * </ul>
 *
 * <p><b>路由决策：</b>当 {@code MessageService.send} 收到发送请求时，按以下顺序匹配路由规则：
 *
 * <ol>
 *   <li>按 {@code (msgType, businessType, tenantId, priority)} 复合索引查匹配规则
 *   <li>按优先级从低到高依次评估规则条件（{@code conditionExpression}）
 *   <li>首条命中的规则返回渠道 ID，未命中则使用默认渠道（{@code DEFAULT_CHANNEL}）
 * </ol>
 *
 * <p><b>多渠道支持：</b>SMS / EMAIL / IN_APP / DINGTALK / FEISHU / WECOM / WEBSOCKET 等。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_ROUTE_RULE_MANAGE} 等权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.server.service.config.RouteRuleService 路由规则服务
 * @see com.njydsz.message.domain.entity.config.MsgRouteRule 路由规则实体
 */
@Tag(name = "路由规则", description = "消息路由规则管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/message/route-rule")
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
  @Idempotent(key = "ydsz:message:RouteRuleController:create:lock", ttlSeconds = 5)
  @Audit(
      module = "路由规则",
      type = AuditType.CONFIG,
      action = AuditAction.CREATE,
      content = "'create'")
  @RateLimit(resource = "message.routerule.create", threshold = 50)
  @PostMapping
  public YdszResponse<MsgRouteRuleVO> create(@Valid @RequestBody RouteRuleUpsertDTO dto) {
    return YdszResponse.success(routeRuleService.create(dto));
  }

  /**
   * 更新路由规则。
   *
   * @param id 规则 ID
   * @param dto 路由规则保存请求体
   * @return 统一响应结果，包含更新后规则详情
   */
  @Operation(summary = "更新路由规则")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_UPDATE)
  @Idempotent(key = "ydsz:message:RouteRuleController:update:lock", ttlSeconds = 5)
  @Audit(
      module = "路由规则",
      type = AuditType.CONFIG,
      action = AuditAction.UPDATE,
      content = "'update'")
  @RateLimit(resource = "message.routerule.update", threshold = 50)
  @PutMapping("/{id}")
  public YdszResponse<MsgRouteRuleVO> update(
      @PathVariable String id, @Valid @RequestBody RouteRuleUpsertDTO dto) {
    return YdszResponse.success(routeRuleService.update(id, dto));
  }

  /**
   * 删除路由规则。
   *
   * @param id 规则 ID
   * @return 统一响应结果
   */
  @Operation(summary = "删除路由规则")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_DELETE)
  @Idempotent(key = "ydsz:message:RouteRuleController:delete:lock", ttlSeconds = 5)
  @Audit(
      module = "路由规则",
      type = AuditType.CONFIG,
      action = AuditAction.DELETE,
      content = "'delete'")
  @RateLimit(resource = "message.routerule.delete", threshold = 50)
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable String id) {
    routeRuleService.delete(id);
    return YdszResponse.success();
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
  public YdszResponse<MsgRouteRuleVO> getById(@PathVariable String id) {
    return YdszResponse.success(routeRuleService.getById(id));
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
  public YdszResponse<PageResponse<List<MsgRouteRuleVO>>> page(PageQuery query) {
    return YdszResponse.success(routeRuleService.page(query));
  }

  /**
   * 查询全部启用的路由规则。
   *
   * @return 统一响应结果，包含启用的路由规则列表
   */
  @Operation(summary = "查询启用的路由规则")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_ROUTE_RULE_LIST)
  @GetMapping("/enabled")
  public YdszResponse<List<MsgRouteRuleVO>> listEnabled() {
    return YdszResponse.success(routeRuleService.listEnabled());
  }
}
