package com.njydsz.pmis.workflow.web.controller.notification;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.domain.dto.notification.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.domain.entity.notification.FlowCcDO;
import com.njydsz.pmis.workflow.server.service.notification.FlowCcService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 抄送中心 Controller
 *
 * <p>P0-3: 抄送中心相关接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-cc", description = "工作流抄送中心接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowCcController {

    /** P0-3: 抄送服务 */
    private final FlowCcService ccService;

    /**
     * P0-3: 抄送中心 - 分页查询
     *
     * @param query 查询条件
     * @return 抄送分页结果
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/cc/page")
    @PrePermission(PermissionCodes.WORKFLOW_CC_VIEW)
    public Result<PageResult<FlowCcDO>> pageCc(@Valid @RequestBody FlowCcQueryDTO query) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        String userId = SecurityContext.getUserId();
        int pageNo = (int) query.getPage();
        int pageSize = (int) query.getSize();
        return Result.ok(ccService.listCcByUser(userId, query.getReadStatus(),
                query.getFlowCode(), tenantId, pageNo, pageSize));
    }

    /**
     * P0-3: 抄送未读数（前端导航栏徽标）。
     *
     * @return 未读抄送条数
     */
    @GetMapping("/cc/unreadCount")
    public Result<Long> ccUnreadCount() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        String userId = SecurityContext.getUserId();
        return Result.ok(ccService.countUnread(userId, tenantId));
    }

    /**
     * P0-3: 抄送标记已读。
     *
     * @param id 抄送记录 ID
     * @return 操作结果
     */
    @Idempotent(key = "flowCc:ccMarkRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/cc/{id}/read")
    public Result<Boolean> ccMarkRead(@PathVariable String id) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        String userId = SecurityContext.getUserId();
        ccService.markRead(tenantId, userId, id);
        return Result.ok(Boolean.TRUE);
    }

    /**
     * P0-3: 抄送全部标记已读。
     *
     * @return 已标记已读的记录数
     */
    @Idempotent(key = "flowCc:ccMarkAllRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/cc/readAll")
    public Result<Integer> ccMarkAllRead() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        String userId = SecurityContext.getUserId();
        return Result.ok(ccService.markAllRead(tenantId, userId));
    }
}
