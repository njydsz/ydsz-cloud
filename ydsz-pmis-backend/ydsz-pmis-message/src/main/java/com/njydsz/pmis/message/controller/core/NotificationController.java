package com.njydsz.pmis.message.controller.core;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.message.dto.core.NotificationQueryDTO;
import com.njydsz.pmis.message.dto.core.NotificationSendDTO;
import com.njydsz.pmis.message.entity.core.MsgNotificationDO;
import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.core.NotificationService;
import com.njydsz.pmis.message.service.receipt.RecallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "站内通知", description = "站内通知发送/收件箱/已读/撤回/推送")
@RestController
@RequestMapping("/notifications")
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
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "notification:send", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/send")
    public Result<Integer> send(@Valid @RequestBody NotificationSendDTO dto) {
        return Result.ok(notificationService.send(dto));
    }

    /**
     * 分页查询当前用户收件箱。
     *
     * @param query 查询参数
     * @return 通知分页结果
     */
    @Operation(summary = "收件箱分页")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/inbox")
    public Result<Page<MsgNotificationDO>> inbox(NotificationQueryDTO query) {
        return Result.ok(notificationService.inbox(SecurityContext.getUserId(), query));
    }

    /**
     * 查询当前用户未读通知数量。
     *
     * @return 统一响应结果，包含未读数量
     */
    @Operation(summary = "未读数量")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/unreadCount")
    public Result<Long> countUnread() {
        return Result.ok(notificationService.countUnread(SecurityContext.getUserId()));
    }

    /**
     * 标记单条通知为已读。
     *
     * @param id 通知 ID
     * @return 统一响应结果，true 表示标记成功
     */
    @Operation(summary = "标记单条已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "notification:markRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/read")
    public Result<Boolean> markRead(@PathVariable String id) {
        return Result.ok(notificationService.markRead(SecurityContext.getUserId(), id));
    }

    /**
     * 将当前用户全部通知标记为已读。
     *
     * @return 统一响应结果，包含已标记条数
     */
    @Operation(summary = "全部标记已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "notification:markAllRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/readAll")
    public Result<Integer> markAllRead() {
        return Result.ok(notificationService.markAllRead(SecurityContext.getUserId()));
    }

    /**
     * 删除通知（仅删当前用户自己的）。
     *
     * @param ids 通知 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "删除通知(仅删自己的)")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_DELETE)
    @Idempotent(key = "notification:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping
    public Result<Void> delete(@Valid @RequestBody List<String> ids) {
        notificationService.delete(SecurityContext.getUserId(), ids);
        return Result.ok();
    }

    /**
     * 撤回通知。
     *
     * @param id 通知 ID
     * @return 统一响应结果，true 表示撤回成功
     */
    @Operation(summary = "撤回通知")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_RECALL)
    @Idempotent(key = "notification:recall", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/recall")
    public Result<Boolean> recall(@PathVariable String id) {
        return Result.ok(recallService.recallNotification(SecurityContext.getUserId(), id));
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
    @PrePermission(PermissionCodes.NOTIF_PUSH)
    @Idempotent(key = "notification:push", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/push")
    public Result<Map<String, Object>> push(
            @RequestParam String userId,
            @RequestParam String type,
            @Valid @RequestBody RealtimePushDTO payload) {
        Object data = payload != null ? payload.getData() : null;
        realtimePushService.pushToUser(userId, type, data);
        return Result.ok(Map.of("success", true, "userId", userId, "type", type));
    }

    /**
     * 广播（实时推送至所有在线用户）。
     *
     * @param type    推送类型
     * @param payload 推送数据
     * @return 统一响应结果，包含广播结果信息
     */
    @Operation(summary = "广播(实时推送所有在线用户)")
    @PrePermission(PermissionCodes.NOTIF_BROADCAST)
    @Idempotent(key = "notification:broadcast", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/broadcast")
    public Result<Map<String, Object>> broadcast(
            @RequestParam String type,
            @Valid @RequestBody Object payload) {
        realtimePushService.broadcast(type, payload);
        return Result.ok(Map.of("success", true, "type", type));
    }
}
