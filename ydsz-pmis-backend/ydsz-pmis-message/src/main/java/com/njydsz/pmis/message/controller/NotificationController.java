package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.message.dto.NotificationQueryDTO;
import com.njydsz.pmis.message.dto.NotificationSendDTO;
import com.njydsz.pmis.message.entity.MsgNotificationDO;
import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.NotificationService;
import com.njydsz.pmis.message.service.RecallService;
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

    private final NotificationService notificationService;
    private final RecallService recallService;
    private final RealtimePushService realtimePushService;

    @Operation(summary = "发送站内通知")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @PostMapping("/send")
    public Result<Integer> send(@Valid @RequestBody NotificationSendDTO dto) {
        return Result.ok(notificationService.send(dto));
    }

    @Operation(summary = "收件箱分页")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/inbox")
    public Result<Page<MsgNotificationDO>> inbox(NotificationQueryDTO query) {
        return Result.ok(notificationService.inbox(SecurityContext.getUserId(), query));
    }

    @Operation(summary = "未读数量")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/unread-count")
    public Result<Long> countUnread() {
        return Result.ok(notificationService.countUnread(SecurityContext.getUserId()));
    }

    @Operation(summary = "标记单条已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @PostMapping("/{id}/read")
    public Result<Boolean> markRead(@PathVariable String id) {
        return Result.ok(notificationService.markRead(SecurityContext.getUserId(), id));
    }

    @Operation(summary = "全部标记已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @PostMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.ok(notificationService.markAllRead(SecurityContext.getUserId()));
    }

    @Operation(summary = "删除通知(仅删自己的)")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_DELETE)
    @DeleteMapping
    public Result<Void> delete(@Valid @RequestBody List<String> ids) {
        notificationService.delete(SecurityContext.getUserId(), ids);
        return Result.ok();
    }

    @Operation(summary = "撤回通知")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_RECALL)
    @PostMapping("/{id}/recall")
    public Result<Boolean> recall(@PathVariable String id) {
        return Result.ok(recallService.recallNotification(SecurityContext.getUserId(), id));
    }

    @Operation(summary = "单推(实时推送指定用户)")
    @PrePermission(PermissionCodes.NOTIF_PUSH)
    @PostMapping("/push")
    public Result<Map<String, Object>> push(
            @RequestParam String userId,
            @RequestParam String type,
            @Valid @RequestBody RealtimePushDTO payload) {
        Object data = payload != null ? payload.getData() : null;
        realtimePushService.pushToUser(userId, type, data);
        return Result.ok(Map.of("success", true, "userId", userId, "type", type));
    }

    @Operation(summary = "广播(实时推送所有在线用户)")
    @PrePermission(PermissionCodes.NOTIF_BROADCAST)
    @PostMapping("/broadcast")
    public Result<Map<String, Object>> broadcast(
            @RequestParam String type,
            @Valid @RequestBody Object payload) {
        realtimePushService.broadcast(type, payload);
        return Result.ok(Map.of("success", true, "type", type));
    }
}
