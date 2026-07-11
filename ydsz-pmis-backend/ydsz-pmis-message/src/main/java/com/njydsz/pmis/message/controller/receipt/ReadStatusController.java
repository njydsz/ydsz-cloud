package com.njydsz.pmis.message.controller.receipt;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.service.receipt.ReadStatusSyncService;
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
@RequestMapping("/message/readStatus")
@RequiredArgsConstructor
public class ReadStatusController {

    /** 已读状态同步服务 */
    private final ReadStatusSyncService readStatusSyncService;

    /**
     * 标记消息为已读。
     *
     * @param msgId  消息 ID
     * @param userId 用户 ID
     * @return 统一响应结果，true 表示标记成功
     */
    @Operation(summary = "标记消息已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "readStatus:markRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/read/{msgId}")
    public Result<Boolean> markRead(@PathVariable String msgId,
                                     @RequestParam String userId) {
        return Result.ok(readStatusSyncService.markRead(msgId, userId));
    }

    /**
     * 批量标记消息为已读。
     *
     * @param msgIds 消息 ID 列表
     * @param userId 用户 ID
     * @return 统一响应结果，包含已标记条数
     */
    @Operation(summary = "批量标记消息已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "readStatus:markReadBatch", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/readBatch")
    public Result<Integer> markReadBatch(@Valid @RequestBody List<String> msgIds,
                                          @RequestParam String userId) {
        return Result.ok(readStatusSyncService.markReadBatch(msgIds, userId));
    }

    /**
     * 标记站内通知为已读。
     *
     * @param notificationId 通知 ID
     * @param userId         用户 ID
     * @return 统一响应结果，true 表示标记成功
     */
    @Operation(summary = "标记站内通知已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "readStatus:markNotificationRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/notification/{notificationId}")
    public Result<Boolean> markNotificationRead(@PathVariable String notificationId,
                                                  @RequestParam String userId) {
        return Result.ok(readStatusSyncService.markNotificationRead(notificationId, userId));
    }

    /**
     * 将用户全部通知标记为已读。
     *
     * @param userId  用户 ID
     * @param bizType 业务类型过滤（可选）
     * @return 统一响应结果，包含已标记条数
     */
    @Operation(summary = "全部通知标记已读")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "readStatus:markAllNotificationsRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/notification/readAll")
    public Result<Integer> markAllNotificationsRead(@RequestParam String userId,
                                                      @RequestParam(required = false) String bizType) {
        return Result.ok(readStatusSyncService.markAllNotificationsRead(userId, bizType));
    }

    /**
     * 查询用户未读消息数量。
     *
     * @param userId  用户 ID
     * @param channel 通道过滤（可选）
     * @return 统一响应结果，包含 total 和 byChannel 两个未读计数
     */
    @Operation(summary = "查询用户未读消息数量")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_VIEW)
    @GetMapping("/unreadCount")
    public Result<Map<String, Long>> getUnreadCount(@RequestParam String userId,
                                                     @RequestParam(required = false) String channel) {
        long total = readStatusSyncService.getUnreadCount(userId);
        long byChannel = channel != null ? readStatusSyncService.getUnreadCountByChannel(userId, channel) : total;
        return Result.ok(Map.of("total", total, "byChannel", byChannel));
    }
}
