package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.service.ReadStatusSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * P1-3: 消息已读/未读状态同步 Controller。
 *
 * <p>提供全通道消息已读状态更新和未读数量查询接口。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Tag(name = "已读状态", description = "消息已读/未读状态同步")
@RestController
@RequestMapping("/message/read-status")
@RequiredArgsConstructor
public class ReadStatusController {

    private final ReadStatusSyncService readStatusSyncService;

    @Operation(summary = "标记消息已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @PostMapping("/read/{msgId}")
    public Result<Boolean> markRead(@PathVariable String msgId,
                                     @RequestParam String userId) {
        return Result.ok(readStatusSyncService.markRead(msgId, userId));
    }

    @Operation(summary = "批量标记消息已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @PostMapping("/read-batch")
    public Result<Integer> markReadBatch(@Valid @RequestBody List<String> msgIds,
                                          @RequestParam String userId) {
        return Result.ok(readStatusSyncService.markReadBatch(msgIds, userId));
    }

    @Operation(summary = "标记站内通知已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @PostMapping("/notification/{notificationId}")
    public Result<Boolean> markNotificationRead(@PathVariable String notificationId,
                                                  @RequestParam String userId) {
        return Result.ok(readStatusSyncService.markNotificationRead(notificationId, userId));
    }

    @Operation(summary = "全部通知标记已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @PostMapping("/notification/read-all")
    public Result<Integer> markAllNotificationsRead(@RequestParam String userId,
                                                      @RequestParam(required = false) String bizType) {
        return Result.ok(readStatusSyncService.markAllNotificationsRead(userId, bizType));
    }

    @Operation(summary = "查询用户未读消息数量")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> getUnreadCount(@RequestParam String userId,
                                                     @RequestParam(required = false) String channel) {
        long total = readStatusSyncService.getUnreadCount(userId);
        long byChannel = channel != null ? readStatusSyncService.getUnreadCountByChannel(userId, channel) : total;
        return Result.ok(Map.of("total", total, "byChannel", byChannel));
    }
}
