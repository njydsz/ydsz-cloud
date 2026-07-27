package com.njydsz.workflow.web.controller.notification;

import java.util.List;

import jakarta.validation.Valid;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.dto.FlowCcQueryDTO;
import com.njydsz.workflow.domain.entity.FlowCcDO;
import com.njydsz.workflow.server.service.FlowCcService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 抄送中心 Controller
 *
 * <p>P0-3: 抄送中心相关接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-team
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
    @RateLimit(resource = "workflow.flowcc.pageCc", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowCcController:pageCc:lock", ttlSeconds = 5)
    @PostMapping("/cc/page")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CC_VIEW)
    public BaseResponse<PageResponse<List<FlowCcDO>>> pageCc(@Valid @RequestBody FlowCcQueryDTO query) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        int pageNo = query.getPageNum();
        int pageSize = query.getPageSize();
        return BaseResponse.success(ccService.listCcByUser(userId, query.getReadStatus(),
                query.getFlowCode(), tenantId, pageNo, pageSize));
    }

    /**
     * P0-3: 抄送未读数（前端导航栏徽标）。
     *
     * @return 未读抄送条数
     */
    @GetMapping("/cc/unreadCount")
    public BaseResponse<Long> ccUnreadCount() {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        return BaseResponse.success(ccService.countUnread(userId, tenantId));
    }

    /**
     * P0-3: 抄送标记已读。
     *
     * @param id 抄送记录 ID
     * @return 操作结果
     */
    @Idempotent(key = "ydsz:workflow:FlowCcController:ccMarkRead:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcc.ccMarkRead", threshold = 50)
    @PostMapping("/cc/{id}/read")
    public BaseResponse<Boolean> ccMarkRead(@PathVariable String id) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        ccService.markRead(tenantId, userId, id);
        return BaseResponse.success(Boolean.TRUE);
    }

    /**
     * P0-3: 抄送全部标记已读。
     *
     * @return 已标记已读的记录数
     */
    @Idempotent(key = "ydsz:workflow:FlowCcController:ccMarkAllRead:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcc.ccMarkAllRead", threshold = 50)
    @PostMapping("/cc/readAll")
    public BaseResponse<Integer> ccMarkAllRead() {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        return BaseResponse.success(ccService.markAllRead(tenantId, userId));
    }
}
