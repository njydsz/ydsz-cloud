package com.njydsz.pmis.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.notification.dto.NotificationQueryDTO;
import com.njydsz.pmis.notification.dto.NotificationSendDTO;
import com.njydsz.pmis.notification.entity.NotificationDO;
import com.njydsz.pmis.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知接口
 */
@Tag(name = "通知中心")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "发送通知")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @OperationLog(module = "通知中心", action = "发送通知", bizType = "NOTIF")
    @PostMapping("/send")
    public R<Integer> send(@Valid @RequestBody NotificationSendDTO dto) {
        return R.ok(notificationService.send(dto));
    }

    @Operation(summary = "我的收件箱")
    @GetMapping("/inbox")
    public R<Page<NotificationDO>> inbox(NotificationQueryDTO query) {
        return R.ok(notificationService.inbox(SecurityContext.getUserId(), query));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(notificationService.countUnread(SecurityContext.getUserId()));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public R<Boolean> markRead(@PathVariable Long id) {
        return R.ok(notificationService.markRead(SecurityContext.getUserId(), id));
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public R<Integer> markAllRead() {
        return R.ok(notificationService.markAllRead(SecurityContext.getUserId()));
    }

    @Operation(summary = "删除通知")
    @DeleteMapping
    public R<Void> delete(@RequestBody List<Long> ids) {
        notificationService.delete(SecurityContext.getUserId(), ids);
        return R.ok();
    }
}
