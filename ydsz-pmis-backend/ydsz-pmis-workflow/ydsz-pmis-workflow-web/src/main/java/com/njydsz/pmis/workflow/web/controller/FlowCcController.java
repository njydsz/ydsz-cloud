package com.njydsz.pmis.workflow.web.controller.notification;

import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.annotation.IdempotentExempt;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.auth.context.AuthContext;
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
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CC_VIEW)
    public BaseResponse<PageResponse<FlowCcDO>> pageCc(@Valid @RequestBody FlowCcQueryDTO query) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        int pageNo = (int) query.getPage();
        int pageSize = (int) query.getSize();
        return BaseResponse.ok(ccService.listCcByUser(userId, query.getReadStatus(),
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
        return BaseResponse.ok(ccService.countUnread(userId, tenantId));
    }

    /**
     * P0-3: 抄送标记已读。
     *
     * @param id 抄送记录 ID
     * @return 操作结果
     */
    @Idempotent(key = "flowCc:ccMarkRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/cc/{id}/read")
    public BaseResponse<Boolean> ccMarkRead(@PathVariable String id) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        ccService.markRead(tenantId, userId, id);
        return BaseResponse.ok(Boolean.TRUE);
    }

    /**
     * P0-3: 抄送全部标记已读。
     *
     * @return 已标记已读的记录数
     */
    @Idempotent(key = "flowCc:ccMarkAllRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/cc/readAll")
    public BaseResponse<Integer> ccMarkAllRead() {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        return BaseResponse.ok(ccService.markAllRead(tenantId, userId));
    }
}
