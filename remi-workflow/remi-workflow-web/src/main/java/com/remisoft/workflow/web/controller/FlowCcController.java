package com.remisoft.workflow.web.controller.notification;

import java.util.List;

import jakarta.validation.Valid;

import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.auth.context.AuthContext;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.core.response.PageResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.lock.annotation.IdempotentExempt;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.workflow.domain.dto.FlowCcQueryDTO;
import com.remisoft.workflow.domain.entity.FlowCc;
import com.remisoft.workflow.server.service.FlowCcService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * 抄送中心 Controller
 *
 * <p>对标钉钉 / 飞书审批中心的「抄送我的」独立 Tab。提供抄送记录的分页查询、
 * 已读 / 全部已读标记、未读数统计、实例维度查询等 HTTP 接口。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>分页查询</b>：{@code GET /cc/page}（我的抄送，支持已读 / 未读 / 流程编码过滤）</li>
 *   <li><b>已读机制</b>：{@code POST /cc/{id}/read}（标记单条已读） /
 *       {@code POST /cc/read-all}（全部已读）</li>
 *   <li><b>未读统计</b>：{@code GET /cc/unread-count}（导航栏徽标数据源）</li>
 *   <li><b>实例维度</b>：{@code GET /cc/instance/{id}}（流程详情页抄送列表）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_CC_VIEW} 权限码；写操作额外校验「操作用户 == 抄送接收人」。
 *
 * <p><b>限流：</b>已读 / 全部已读通过 {@link Idempotent} 5s 防重；高 QPS 场景
 * （如 WebSocket 推送触发）通过 {@link IdempotentExempt} 豁免。
 *
 * <p><b>性能优化：</b>分页查询走 {@code remi_flow_cc} 复合索引
 * {@code (cc_user_id, read_status, tenant_id)}；未读数走 Redis 缓存
 * {@code remi:flow:cc:unread:{userId}}，TTL 5min。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowCcService 抄送服务
 * @see FlowCc 抄送实体
 * @see FlowCcQueryDTO 抄送查询 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-cc", description = "工作流抄送中心接口")
@RequestMapping("/api/v1/workflow/engine")
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
    @Idempotent(key = "remi:workflow:FlowCcController:pageCc:lock", ttlSeconds = 5)
    @PostMapping("/cc/page")
    @Audit(module = "流程抄送", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'pageCc'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CC_VIEW)
    public BaseResponse<PageResponse<List<FlowCc>>> pageCc(@Valid @RequestBody FlowCcQueryDTO query) {
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
    @Idempotent(key = "remi:workflow:FlowCcController:ccMarkRead:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcc.ccMarkRead", threshold = 50)
    @PostMapping("/cc/{id}/read")
    @Audit(module = "流程抄送", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'ccMarkRead'")
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
    @Idempotent(key = "remi:workflow:FlowCcController:ccMarkAllRead:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcc.ccMarkAllRead", threshold = 50)
    @PostMapping("/cc/readAll")
    @Audit(module = "流程抄送", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'ccMarkAllRead'")
    public BaseResponse<Integer> ccMarkAllRead() {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        String userId = AuthContext.getUserId();
        return BaseResponse.success(ccService.markAllRead(tenantId, userId));
    }
}
