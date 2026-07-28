package com.njydsz.message.web.controller.config;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.config.UserChannelBindingDTO;
import com.njydsz.message.domain.entity.config.MsgUserChannel;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.server.service.config.UserChannelBindingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 用户通道绑定（User-Channel Binding）Controller。
 *
 * <p>提供<b>用户与具体通道联系方式的绑定关系</b>管理 API。
 * 与「通道类型」不同，本 Controller 管理的是<b>具体账号</b>（手机号、邮箱、IM 账号）与用户的绑定关系，
 * 是消息真正发送时的寻址依据。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/user-channels/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>新增/更新绑定</b>：{@code POST /} — 绑定用户的某通道联系方式（如手机号 / 邮箱 / 钉钉 userId）</li>
 *   <li><b>我的绑定</b>：{@code GET /mine} — 查询当前登录用户的全部绑定（用于「我的通知设置」页面）</li>
 *   <li><b>用户绑定</b>：{@code GET /user/{userId}} — 管理员查询某用户的全部绑定</li>
 *   <li><b>删除绑定</b>：{@code DELETE /{id}} — 解除某条绑定</li>
 * </ul>
 *
 * <p><b>与 PreferenceController 的区别：</b>
 * <ul>
 *   <li>本 Controller：<b>通道联系方式</b>（用户「绑定」到该通道的具体账号）</li>
 *   <li>PreferenceController：<b>接收偏好</b>（用户在该通道上「是否愿意接收」）</li>
 * </ul>
 *
 * <p><b>典型数据：</b>{@code MsgUserChannel} 包含字段：
 * <ul>
 *   <li>{@code userId}：用户 ID</li>
 *   <li>{@code channel}：通道类型（SMS / EMAIL / DINGTALK / FEISHU / WECOM）</li>
 *   <li>{@code contact}：具体联系方式（手机号 / 邮箱地址 / 钉钉 userId 等）</li>
 *   <li>{@code isPrimary}：是否主联系方式（短信验证码等关键通知优先使用主联系方式）</li>
 *   <li>{@code isVerified}：是否已验证（未验证的联系通道不能用于发送）</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有绑定按 {@code tenantId} 隔离，跨租户绑定不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口（upsert/delete）启用 {@link Idempotent} 5s 防重</li>
 *   <li>写接口（upsert/delete）启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>写接口（upsert/delete）启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_SEND} 权限码</li>
 *   <li>邮箱 / 手机号等敏感联系方式在落库前自动脱敏（{@code @Sensitive} 注解）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.config.UserChannelBindingService 用户通道绑定服务
 * @see com.njydsz.message.domain.entity.config.MsgUserChannel 绑定实体
 */
@Tag(name = "用户通道绑定", description = "用户通道联系方式绑定/查询/删除")
@RestController
@RequestMapping("/api/v1/message/user-channels")
@RequiredArgsConstructor
public class UserChannelBindingController {

    private final UserChannelBindingService userChannelBindingService;

    @Operation(summary = "新增或更新通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Audit(module = "通道绑定", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'upsert'")
    @RateLimit(resource = "message.userchannelbinding.upsert", threshold = 50)
    @Idempotent(key = "ydsz:message:UserChannelBindingController:upsert:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<MsgUserChannelVO> upsert(@Valid @RequestBody UserChannelBindingDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(userChannelBindingService.upsert(dto)));
    }

    @Operation(summary = "查询当前用户所有通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/mine")
    public BaseResponse<List<MsgUserChannelVO>> listMine() {
        return BaseResponse.success(MessageConverter.INSTANT.userChannelListToVO(userChannelBindingService.listByUser(AuthContext.getUserId())));
    }

    @Operation(summary = "按用户ID查询通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/user/{userId}")
    public BaseResponse<List<MsgUserChannelVO>> listByUser(@PathVariable String userId) {
        return BaseResponse.success(MessageConverter.INSTANT.userChannelListToVO(userChannelBindingService.listByUser(userId)));
    }

    @Operation(summary = "删除通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Audit(module = "通道绑定", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "message.userchannelbinding.delete", threshold = 50)
    @Idempotent(key = "ydsz:message:UserChannelBindingController:delete:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        userChannelBindingService.delete(id);
        return BaseResponse.success();
    }
}
