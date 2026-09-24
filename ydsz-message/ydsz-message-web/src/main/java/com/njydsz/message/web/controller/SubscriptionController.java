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
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.SubscriptionUpsertDTO;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.service.config.SubscriptionService;

/**
 * 订阅关系 Controller。
 *
 * <p>提供<b>用户订阅主题关系</b>的 HTTP API，支撑「订阅 - 发布」通知模式： 用户订阅感兴趣的主题（{@code topicCode}），系统按主题批量推送消息。
 *
 * <p><b>接口路径：</b>{@code /api/message/subscription/**}
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
 * @since 26.09.01
 * @see com.njydsz.message.server.service.config.SubscriptionService 订阅服务
 * @see com.njydsz.message.domain.entity.config.MsgSubscription 订阅实体
 */
@Tag(name = "消息订阅", description = "用户主题订阅关系管理")
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/message/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

  /** 订阅关系服务 */
  private final SubscriptionService subscriptionService;

  /**
   * 新增或更新用户订阅关系。
   *
   * <p>覆盖式保存用户的 (topicCode, channel) 订阅关系；已存在则更新，不存在则新增。
   * 启用 5s 幂等防重、50 QPS 限流，并记录审计日志。
   *
   * @param dto 订阅保存请求体（经 {@code @Valid} 校验；含 topicCode / channel / userId 等）
   * @return 订阅记录 VO（含订阅 ID、用户 ID、主题编码、通道）
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
   * <p>查询某用户在所有主题 / 通道组合下的订阅记录。
   *
   * @param userId 用户 ID（路径变量，不可为空）
   * @return 订阅列表（无订阅时返回空列表）
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
   * <p>查询某主题在某通道下的全部订阅用户，用于消息发送时的 fan-out 分发。
   *
   * @param topicCode 主题编码（路径变量，不可为空）
   * @param channel 通道（路径变量，如 SMS / EMAIL / PUSH）
   * @return 订阅列表（无订阅时返回空列表）
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
   * <p>取消 (userId, topicCode, channel) 组合的订阅关系；幂等：重复退订不报错。
   *
   * @param userId 用户 ID（Query 参数）
   * @param topicCode 主题编码（Query 参数）
   * @param channel 通道（Query 参数）
   * @return 无业务数据（仅返回操作成功标识）
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
