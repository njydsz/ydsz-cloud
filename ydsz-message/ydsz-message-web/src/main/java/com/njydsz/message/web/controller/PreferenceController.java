package com.njydsz.message.web.controller.config;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.domain.dto.config.PreferenceUpsertDTO;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.server.service.config.PreferenceService;

/**
 * 用户消息偏好（Preference）Controller。
 *
 * <p>提供<b>用户级消息偏好管理</b>的 HTTP API。 偏好用于细粒度控制用户在「什么通道 / 什么业务类型 / 什么时段」愿意接收通知， 与 {@code
 * SubscriptionController}（订阅-发布主题）配合实现完整的消息触达策略。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/preference/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>新增/更新偏好</b>：{@code POST /} — 设置 (userId, channel, bizType) 的接收偏好（启用/免打扰/时段）
 *   <li><b>用户偏好列表</b>：{@code GET /{userId}} — 查询某用户的全部偏好
 *   <li><b>精确查询</b>：{@code GET /{userId}/{channel}/{bizType}} — 查询某用户在某通道某业务类型的偏好
 *   <li><b>删除偏好</b>：{@code DELETE /{id}} — 删除指定偏好记录
 * </ul>
 *
 * <p><b>偏好的优先级：</b>用户偏好 &gt; 路由规则 &gt; 模板默认设置。 当某条消息触达用户时，按以下顺序检查：
 *
 * <ol>
 *   <li>用户偏好（{@code MsgPreference}）：用户是否禁用了该通道 / 该业务类型 / 该时段
 *   <li>路由规则（{@code MsgRouteRule}）：按消息类型 / 业务类型 / 用户属性选择通道
 *   <li>模板默认设置：未匹配到规则时使用模板的默认通道
 * </ol>
 *
 * <p><b>与 SubscriptionController 的区别：</b>
 *
 * <ul>
 *   <li>本 Controller：<b>按通道 / 业务类型</b>的细粒度偏好（用户希望「订单通知用邮件而非短信」）
 *   <li>SubscriptionController：<b>按主题订阅</b>（用户订阅了「项目立项」主题才会收到）
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有偏好按 {@code tenantId} 隔离，跨租户偏好不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（upsert/delete）启用 {@link Idempotent} 5s 防重
 *   <li>写接口（upsert/delete）启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口（upsert/delete）启用 {@link Audit} 审计日志（异步持久化）
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_PREFERENCE_UPDATE}
 *       等权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.config.PreferenceService 用户偏好服务
 * @see com.njydsz.message.domain.entity.config.MsgPreference 偏好实体
 */
@Tag(name = "消息偏好", description = "用户消息偏好管理")
@Slf4j
@RequestMapping("/api/v1/message/preference")
@RequiredArgsConstructor
public class PreferenceController {

  /** 用户消息偏好服务 */
  private final PreferenceService preferenceService;

  /**
   * 新增或更新用户消息偏好。
   *
   * @param dto 偏好保存请求体
   * @return 统一响应结果，包含偏好记录
   */
  @Operation(summary = "新增/更新偏好")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_UPDATE)
  @Idempotent(key = "ydsz:message:PreferenceController:upsert:lock", ttlSeconds = 5)
  @Audit(
      module = "偏好设置",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'upsert'")
  @RateLimit(resource = "message.preference.upsert", threshold = 50)
  @PostMapping
  public YdszResponse<MsgPreferenceVO> upsert(@Valid @RequestBody PreferenceUpsertDTO dto) {
    return YdszResponse.success(MessageConverter.INSTANT.entityToVO(preferenceService.upsert(dto)));
  }

  /**
   * 查询用户全部消息偏好。
   *
   * @param userId 用户 ID
   * @return 统一响应结果，包含偏好列表
   */
  @Operation(summary = "查询用户所有偏好")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_VIEW)
  @GetMapping("/{userId}")
  public YdszResponse<List<MsgPreferenceVO>> listByUser(@PathVariable String userId) {
    return YdszResponse.success(
        MessageConverter.INSTANT.preferenceListToVO(preferenceService.listByUser(userId)));
  }

  /**
   * 按用户、通道和业务类型查询偏好。
   *
   * @param userId 用户 ID
   * @param channel 通道
   * @param bizType 业务类型
   * @return 统一响应结果，包含偏好记录
   */
  @Operation(summary = "按用户+通道+业务类型查询偏好")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_VIEW)
  @GetMapping("/{userId}/{channel}/{bizType}")
  public YdszResponse<MsgPreferenceVO> getByUser(
      @PathVariable String userId, @PathVariable String channel, @PathVariable String bizType) {
    return YdszResponse.success(
        MessageConverter.INSTANT.entityToVO(preferenceService.getByUser(userId, channel, bizType)));
  }

  /**
   * 删除用户消息偏好。
   *
   * @param id 偏好记录 ID
   * @return 统一响应结果
   */
  @Operation(summary = "删除偏好")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_DELETE)
  @Idempotent(key = "ydsz:message:PreferenceController:delete:lock", ttlSeconds = 5)
  @Audit(
      module = "偏好设置",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  @RateLimit(resource = "message.preference.delete", threshold = 50)
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable String id) {
    preferenceService.delete(id);
    return YdszResponse.success();
  }
}
