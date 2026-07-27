package com.njydsz.message.web.controller.core;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.dto.RealtimePushDTO;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.core.NotificationQueryDTO;
import com.njydsz.message.domain.dto.core.NotificationSendDTO;
import com.njydsz.message.domain.entity.core.MsgNotification;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 站内通知 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "站内通知", description = "站内通知发送/收件箱/已读/撤回/推送")
@RestController
@RequestMapping("/api/v1/message/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /** 站内通知服务 */
    private final NotificationService notificationService;
    /** 消息撤回服务 */
    private final RecallService recallService;
    /** 实时推送服务（WebSocket） */
    private final RealtimePushService realtimePushService;

    /**
     * 发送站内通知。
     *
     * @param dto 通知发送请求体
     * @return 统一响应结果，包含发送条数
     */
    @Operation(summary = "发送站内通知")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "ydsz:message:NotificationController:send:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'send'")
    @RateLimit(resource = "message.notification.send", threshold = 50)
    @PostMapping("/send")
    public BaseResponse<Integer> send(@Valid @RequestBody NotificationSendDTO dto) {
        return BaseResponse.success(notificationService.send(dto));
    }

    /**
     * 分页查询当前用户收件箱。
     *
     * @param query 查询参数
     * @return 通知分页结果
     */
    @Operation(summary = "收件箱分页")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/inbox")
    public BaseResponse<Page<MsgNotificationVO>> inbox(NotificationQueryDTO query) {
        Page<MsgNotification> page = notificationService.inbox(AuthContext.getUserId(), query);
        Page<MsgNotificationVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(MessageConverter.INSTANT.notificationListToVO(page.getRecords()));
        return BaseResponse.success(voPage);
    }

    /**
     * 查询当前用户未读通知数量。
     *
     * @return 统一响应结果，包含未读数量
     */
    @Operation(summary = "未读数量")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/unreadCount")
    public BaseResponse<Long> countUnread() {
        return BaseResponse.success(notificationService.countUnread(AuthContext.getUserId()));
    }

    /**
     * 标记单条通知为已读。
     *
     * @param id 通知 ID
     * @return 统一响应结果，true 表示标记成功
     */
    @Operation(summary = "标记单条已读")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "ydsz:message:NotificationController:markRead:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'markRead'")
    @RateLimit(resource = "message.notification.markRead", threshold = 50)
    @PostMapping("/{id}/read")
    public BaseResponse<Boolean> markRead(@PathVariable String id) {
        return BaseResponse.success(notificationService.markRead(AuthContext.getUserId(), id));
    }

    /**
     * 将当前用户全部通知标记为已读。
     *
     * @return 统一响应结果，包含已标记条数
     */
    @Operation(summary = "全部标记已读")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "ydsz:message:NotificationController:markAllRead:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'markAllRead'")
    @RateLimit(resource = "message.notification.markAllRead", threshold = 50)
    @PostMapping("/readAll")
    public BaseResponse<Integer> markAllRead() {
        return BaseResponse.success(notificationService.markAllRead(AuthContext.getUserId()));
    }

    /**
     * 删除通知（仅删当前用户自己的）。
     *
     * @param ids 通知 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "删除通知(仅删自己的)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_DELETE)
    @Idempotent(key = "ydsz:message:NotificationController:delete:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "message.notification.delete", threshold = 50)
    @DeleteMapping
    public BaseResponse<Void> delete(@Valid @RequestBody List<String> ids) {
        notificationService.delete(AuthContext.getUserId(), ids);
        return BaseResponse.success();
    }

    /**
     * 撤回通知。
     *
     * @param id 通知 ID
     * @return 统一响应结果，true 表示撤回成功
     */
    @Operation(summary = "撤回通知")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_RECALL)
    @Idempotent(key = "ydsz:message:NotificationController:recall:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'recall'")
    @RateLimit(resource = "message.notification.recall", threshold = 50)
    @PostMapping("/{id}/recall")
    public BaseResponse<Boolean> recall(@PathVariable String id) {
        return BaseResponse.success(recallService.recallNotification(AuthContext.getUserId(), id));
    }

    /**
     * 单推（实时推送至指定用户）。
     *
     * @param userId  目标用户 ID
     * @param type    推送类型
     * @param payload 推送数据
     * @return 统一响应结果，包含推送结果信息
     */
    @Operation(summary = "单推(实时推送指定用户)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_PUSH)
    @Idempotent(key = "ydsz:message:NotificationController:push:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/push")
    public BaseResponse<Map<String, Object>> push(
            @RequestParam String userId,
            @RequestParam String type,
            @Valid @RequestBody RealtimePushDTO payload) {
        Object data = payload != null ? payload.getData() : null;
        realtimePushService.pushToUser(userId, type, data);
        return BaseResponse.success(Map.of("success", true, "userId", userId, "type", type));
    }

    /**
     * 广播（实时推送至所有在线用户）。
     *
     * @param type    推送类型
     * @param payload 推送数据
     * @return 统一响应结果，包含广播结果信息
     */
    @Operation(summary = "广播(实时推送所有在线用户)")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_BROADCAST)
    @Idempotent(key = "ydsz:message:NotificationController:broadcast:lock", ttlSeconds = 5)
    @Audit(module = "通知管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/broadcast")
    public BaseResponse<Map<String, Object>> broadcast(
            @RequestParam String type,
            @Valid @RequestBody Object payload) {
        realtimePushService.broadcast(type, payload);
        return BaseResponse.success(Map.of("success", true, "type", type));
    }
}
