package com.njydsz.pmis.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;
import com.njydsz.pmis.system.dto.NotificationQueryDTO;
import com.njydsz.pmis.system.dto.NotificationSendDTO;
import com.njydsz.pmis.system.entity.NotificationDO;
import com.njydsz.pmis.system.service.NotificationService;
import com.njydsz.pmis.system.service.RealtimePushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "通知中心", description = "通知发送、收件箱及实时推送接口")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    /** 通知服务 */
    private final NotificationService notificationService;
    /** 实时推送服务（WebSocket，P0-2） */
    private final RealtimePushService realtimePushService;

    /**
     * 发送通知
     *
     * @param dto 通知发送表单
     * @return 统一响应结果，包含实际插入条数
     */
    @Operation(summary = "发送通知")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @OperationLog(module = "通知中心", action = "发送通知", bizType = "NOTIF")
    @PostMapping("/send")
    public Result<Integer> send(@Valid @RequestBody NotificationSendDTO dto) {
        return Result.ok(notificationService.send(dto));
    }

    /**
     * 我的收件箱分页查询
     *
     * @param query 查询条件
     * @return 统一响应结果，包含通知分页数据
     */
    @Operation(summary = "我的收件箱")
    @GetMapping("/inbox")
    public Result<Page<NotificationDO>> inbox(@Valid NotificationQueryDTO query) {
        return Result.ok(notificationService.inbox(SecurityContext.getUserId(), query));
    }

    /**
     * 查询当前用户未读通知数量
     *
     * @return 统一响应结果，包含未读数量
     */
    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.countUnread(SecurityContext.getUserId()));
    }

    /**
     * 标记单条通知已读
     *
     * @param id 通知 ID
     * @return 统一响应结果，包含是否标记成功
     */
    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public Result<Boolean> markRead(
            @Parameter(description = "通知ID") @PathVariable String id) {
        return Result.ok(notificationService.markRead(SecurityContext.getUserId(), id));
    }

    /**
     * 全部标记已读
     *
     * @return 统一响应结果，包含实际标记条数
     */
    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.ok(notificationService.markAllRead(SecurityContext.getUserId()));
    }

    /**
     * 删除通知（逻辑删除，仅允许删除属于自己的通知）
     *
     * @param ids 通知 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "删除通知")
    @OperationLog(module = "通知中心", action = "删除通知", bizType = "NOTIF")
    @DeleteMapping
    public Result<Void> delete(@RequestBody List<String> ids) {
        notificationService.delete(SecurityContext.getUserId(), ids);
        return Result.ok();
    }

    /**
     * 实时推送消息到指定用户（供其他微服务通过 Feign 调用，P0-2）
     *
     * <p>P0-2 修复：补 @PrePermission 权限校验，防止任意登录用户调用向他人推送消息。
     * 内部 Feign 调用应使用服务账号（拥有 notif:message:push 权限）。</p>
     *
     * @param userId  接收用户 ID
     * @param type    消息类型 (NOTIFICATION/ALERT/DASHBOARD)
     * @param payload 消息内容
     * @return 推送结果
     */
    @Operation(summary = "实时推送（指定用户）")
    @PrePermission(PermissionCodes.NOTIF_PUSH)
    @PostMapping("/push")
    public Result<Map<String, Object>> push(
            @Parameter(description = "接收用户ID") @RequestParam String userId,
            @Parameter(description = "消息类型") @RequestParam String type,
            @RequestBody RealtimePushDTO payload) {
        Object data = payload != null ? payload.getData() : null;
        realtimePushService.pushToUser(userId, type, data);
        return Result.ok(Map.of("success", true, "userId", userId, "type", type));
    }

    /**
     * 广播消息到所有在线用户（供其他微服务通过 Feign 调用，P0-2）
     *
     * <p>P0-2 修复：补 @PrePermission 权限校验，防止任意登录用户调用全站广播。
     * 内部 Feign 调用应使用服务账号（拥有 notif:message:broadcast 权限）。</p>
     *
     * @param type    消息类型
     * @param payload 消息内容
     * @return 推送结果
     */
    @Operation(summary = "实时广播")
    @PrePermission(PermissionCodes.NOTIF_BROADCAST)
    @PostMapping("/broadcast")
    public Result<Map<String, Object>> broadcast(
            @Parameter(description = "消息类型") @RequestParam String type,
            @RequestBody Object payload) {
        realtimePushService.broadcast(type, payload);
        return Result.ok(Map.of("success", true, "type", type));
    }
}
