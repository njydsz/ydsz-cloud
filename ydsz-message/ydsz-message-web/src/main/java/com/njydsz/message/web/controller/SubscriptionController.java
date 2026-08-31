package com.njydsz.message.web.controller.config;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.SubscriptionUpsertDTO;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.service.config.SubscriptionService;

/**
 * 订阅关系 Controller。
 *
 * <p>提供<b>用户订阅主题关系</b>的 HTTP API，支撑「订阅 - 发布」通知模式： 用户订阅感兴趣的主题（{@code topicCode}），系统按主题批量推送消息。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/subscription/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>订阅 / 取消订阅</b>：{@code POST /upsert} 增改订阅（覆盖式）/ {@code POST /{topicCode}/cancel} 取消订阅
 *   <li><b>我的订阅</b>：{@code GET /mine} 当前用户订阅的全部主题
 *   <li><b>订阅者</b>：{@code GET /topic/{topicCode}} 查询某主题的全部订阅者（发送时 fan-out 用）
 *   <li><b>订阅状态</b>：{@code GET /exists} 判断当前用户是否订阅某主题
 * </ul>
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>用户订阅「项目立项」主题，所有立项通知自动推送给该用户
 *   <li>用户订阅「系统公告」主题，平台公告自动推送给该用户
 *   <li>用户订阅「我审批的」主题，ydsz-workflow 待办变更自动推送给该用户
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有操作按 {@code tenantId} 隔离，跨租户订阅不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验订阅管理权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.config.SubscriptionService 订阅服务
 * @see com.njydsz.message.domain.entity.config.MsgSubscription 订阅实体
 */
@Tag(name = "消息订阅", description = "用户主题订阅关系管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/message/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

  /** 订阅关系服务 */
  private final SubscriptionService subscriptionService;

  /**
   * 新增或更新用户订阅关系。
   *
   * @param dto 订阅保存请求体
   * @return 统一响应结果，包含订阅记录
   */
  @Operation(summary = "新增/更新订阅")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_UPDATE)
  @Idempotent(key = "ydsz:message:SubscriptionController:upsert:lock", ttlSeconds = 5)
  @Audit(
      module = "订阅管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'upsert'")
  @RateLimit(resource = "message.subscription.upsert", threshold = 50)
  @PostMapping
  public YdszResponse<MsgSubscriptionVO> upsert(@Valid @RequestBody SubscriptionUpsertDTO dto) {
    return YdszResponse.success(subscriptionService.upsert(dto));
  }

  /**
   * 查询用户全部订阅关系。
   *
   * @param userId 用户 ID
   * @return 统一响应结果，包含订阅列表
   */
  @Operation(summary = "查询用户所有订阅")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_LIST)
  @GetMapping("/user/{userId}")
  public YdszResponse<List<MsgSubscriptionVO>> listByUser(@PathVariable String userId) {
    return YdszResponse.success(subscriptionService.listByUser(userId));
  }

  /**
   * 按主题和通道查询订阅列表。
   *
   * @param topicCode 主题编码
   * @param channel 通道（SMS/EMAIL/PUSH 等）
   * @return 统一响应结果，包含订阅列表
   */
  @Operation(summary = "按主题+通道查询订阅")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_LIST)
  @GetMapping("/topic/{topicCode}/{channel}")
  public YdszResponse<List<MsgSubscriptionVO>> listByTopic(
      @PathVariable String topicCode, @PathVariable String channel) {
    return YdszResponse.success(
        subscriptionService.listByTopic(topicCode, channel));
  }

  /**
   * 退订指定主题和通道。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 通道
   * @return 统一响应结果
   */
  @Operation(summary = "退订")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_DELETE)
  @Idempotent(key = "ydsz:message:SubscriptionController:unsubscribe:lock", ttlSeconds = 5)
  @Audit(
      module = "订阅管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'unsubscribe'")
  @RateLimit(resource = "message.subscription.unsubscribe", threshold = 50)
  @PostMapping("/unsubscribe")
  public YdszResponse<Void> unsubscribe(
      @RequestParam String userId, @RequestParam String topicCode, @RequestParam String channel) {
    subscriptionService.unsubscribe(userId, topicCode, channel);
    return YdszResponse.success();
  }
}
