package com.njydsz.pmis.workflow.controller.instance;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.entity.instance.FlowInboxDO;
import com.njydsz.pmis.workflow.service.instance.FlowInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站内信控制器（P2-4）
 *
 * <p>提供站内信查询、已读标记、未读统计等 REST API。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@RestController
@RequestMapping("/flow/inbox")
@RequiredArgsConstructor
public class FlowInboxController {

    /** 站内信服务，负责消息查询、已读标记与未读统计 */
    private final FlowInboxService inboxService;

    /**
     * 分页查询站内信列表。
     *
     * @param onlyUnread 是否只查未读（默认 false）
     * @param page       页码（从 1 开始，默认 1）
     * @param size       每页大小（默认 20，最大 100）
     * @return 分页数据（items / total / page / size）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "false") boolean onlyUnread,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = getCurrentUserId();
        String tenantId = TenantContext.getTenantId();
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        int offset = (page - 1) * size;

        List<FlowInboxDO> items = inboxService.listInbox(userId, tenantId, onlyUnread, offset, size);
        long total = inboxService.countUnread(userId, tenantId);

        return Result.ok(Map.of(
                "items", items,
                "total", total,
                "page", page,
                "size", size
        ));
    }

    /**
     * 统计未读站内信数。
     *
     * @return 未读条数
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        String userId = getCurrentUserId();
        String tenantId = TenantContext.getTenantId();
        return Result.ok(inboxService.countUnread(userId, tenantId));
    }

    /**
     * 标记单条站内信为已读。
     *
     * @param id 站内信 ID
     * @return 空响应
     */
    @Idempotent(key = "flow-inbox:mark-read", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable String id) {
        String userId = getCurrentUserId();
        inboxService.markRead(id, userId);
        return Result.ok();
    }

    /**
     * 批量标记已读。
     *
     * @param body 请求体，含 ids 列表
     * @return 已标记已读的条数
     */
    @Idempotent(key = "flow-inbox:batch-mark-read", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/batch-read")
    public Result<Integer> batchMarkRead(@RequestBody Map<String, List<String>> body) {
        String userId = getCurrentUserId();
        String tenantId = TenantContext.getTenantId();
        List<String> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.ok(0);
        }
        return Result.ok(inboxService.batchMarkRead(ids, userId, tenantId));
    }

    /**
     * 全部标记已读。
     *
     * @return 已标记已读的条数
     */
    @Idempotent(key = "flow-inbox:mark-all-read", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        String userId = getCurrentUserId();
        String tenantId = TenantContext.getTenantId();
        return Result.ok(inboxService.markAllRead(userId, tenantId));
    }

    /**
     * 获取当前登录用户 ID，未登录时抛出 401 异常。
     *
     * @return 当前用户 ID
     */
    private String getCurrentUserId() {
        String userId = SecurityContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new BizException(BizErrorCode.UNAUTHORIZED, "用户未登录");
        }
        return userId;
    }
}
