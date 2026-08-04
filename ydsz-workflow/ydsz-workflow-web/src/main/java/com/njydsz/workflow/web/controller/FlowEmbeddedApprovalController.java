package com.njydsz.workflow.web.controller.integration;

import jakarta.validation.Valid;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.security.LoginUser;
import com.njydsz.workflow.domain.dto.EmbeddedApprovalActionDTO;
import com.njydsz.workflow.server.service.FlowEmbeddedApprovalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.dto.EmbeddedApprovalViewDTO;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 嵌入式审批 Controller（P2-2）
 *
 * <p>提供<b>业务侧嵌入式审批面板</b>的 HTTP API，让业务页（项目立项 / 合同 / 工时 / 采购等）
 * 无需跳转到独立审批中心即可完成"查看/通过/驳回/转办/催办/撤回"等操作。
 *
 * <p><b>核心价值：</b>降低业务用户操作成本——审批动作嵌入业务流，避免"业务页 ↔ 审批中心"来回切换。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/embedded/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>面板加载</b>：{@code GET /panel} 按 (businessType, businessId) 一次性返回实例/当前待办/历史轨迹/myRole/actions 等全部数据</li>
 *   <li><b>快捷操作</b>：{@code POST /action} / {@code POST /{businessType}/{businessId}/action} 提供
 *       PASS/REJECT/TRANSFER/DELEGATE/URGE/WITHDRAW 等嵌入式按钮</li>
 * </ul>
 *
 * <p><b>与 FlowTaskController 的区别：</b>
 * <ul>
 *   <li>本 Controller：<b>业务端</b>，按 businessType + businessId 操作，仅暴露嵌入式场景所需最小集</li>
 *   <li>FlowTaskController：<b>管理端/审批中心</b>，按 taskId 操作，提供完整能力</li>
 * </ul>
 *
 * <p><b>多业务类型支持：</b>{@code businessType} 可取值
 * {@code PROJECT_INITIATION}（项目立项）/ {@code CONTRACT}（合同）/ {@code TIMESHEET}（工时）/
 * {@code PURCHASE}（采购）等，由各业务模块注册 {@code FlowEmbeddedApprovalService} 的解析逻辑。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重（避免双击重复审批）</li>
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>用户身份优先取 SecurityContext（防止前端伪造 userId）</li>
 *   <li>WITHDRAW 操作仅发起人可执行（由 Service 层校验）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.server.service.FlowEmbeddedApprovalService 嵌入式审批服务
 * @see com.njydsz.workflow.domain.dto.EmbeddedApprovalActionDTO 嵌入式快捷操作 DTO
 * @see com.njydsz.workflow.domain.dto.EmbeddedApprovalViewDTO 嵌入式面板视图 DTO
 */
@Slf4j
@Tag(name = "嵌入式审批")
@RestController
@RequestMapping("/api/v1/workflow/embedded")
@RequiredArgsConstructor
@Validated
public class FlowEmbeddedApprovalController {

    /** 嵌入式审批服务，负责业务侧审批面板数据加载与快捷操作 */
    private final FlowEmbeddedApprovalService embeddedApprovalService;

    /**
     * 加载嵌入式审批面板（聚合查询）
     *
     * <p>业务页挂载面板时调用一次，返回实例/当前待办/历史轨迹/myRole/actions 等全部数据。
     *
     * @param businessType 业务类型（PROJECT_INITIATION / CONTRACT / TIMESHEET / PURCHASE ...）
     * @param businessId   业务 ID
     * @param userId       当前用户 ID（可空，空时取 SecurityContext）
     * @return 嵌入式审批面板视图
     */
    @Operation(summary = "加载嵌入式审批面板")
    @GetMapping("/panel")
    public BaseResponse<EmbeddedApprovalViewDTO> loadPanel(@RequestParam String businessType,
                                                     @RequestParam String businessId,
                                                     @RequestParam(required = false) String userId) {
        String uid = userId;
        if (uid == null) {
            LoginUser u = AuthContext.getCurrentOrNull();
            if (u != null) {
                uid = u.getUserId();
            }
        }
        if (uid == null) {
            return BaseResponse.error(BaseResultCode.UNAUTHORIZED, "未登录");
        }
        return BaseResponse.success(embeddedApprovalService.loadPanel(businessType, businessId, uid));
    }

    /**
     * 嵌入式快捷操作
     *
     * <p>业务页嵌入式按钮调用：
     * <ul>
     *   <li>PASS/REJECT — 通过/驳回（自动找 mine 任务）</li>
     *   <li>TRANSFER/DELEGATE — 转办/委派（需 targetUserId）</li>
     *   <li>URGE — 催办</li>
     *   <li>WITHDRAW — 撤回（仅发起人可执行）</li>
     * </ul>
     *
     * @param dto 嵌入式快捷操作参数
     */
    @Operation(summary = "嵌入式快捷操作")
    @Idempotent(key = "ydsz:workflow:FlowEmbeddedApprovalController:quickAction:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowembeddedapproval.quickAction", threshold = 50)
    @PostMapping("/action")
    @Audit(module = "嵌入式审批", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'quickAction'")
    public BaseResponse<Void> quickAction(@Valid @RequestBody EmbeddedApprovalActionDTO dto) {
        LoginUser u = AuthContext.getCurrentOrNull();
        if (dto.getUserId() == null && u != null) {
            dto.setUserId(u.getUserId());
        }
        if (dto.getUserName() == null && u != null) {
            dto.setUserName(u.getUsername());
        }
        embeddedApprovalService.quickAction(dto);
        return BaseResponse.success();
    }

    /**
     * 嵌入式快捷操作（按业务类型 + 业务 ID）。
     *
     * <p>URL 形式：/workflow/embedded/{businessType}/{businessId}/action
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @param dto          嵌入式快捷操作参数
     * @return 空响应
     */
    @Operation(summary = "嵌入式快捷操作（按业务类型+业务ID）")
    @Idempotent(key = "ydsz:workflow:FlowEmbeddedApprovalController:quickActionByPath:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowembeddedapproval.quickActionByPath", threshold = 50)
    @PostMapping("/{businessType}/{businessId}/action")
    @Audit(module = "嵌入式审批", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'quickActionByPath'")
    public BaseResponse<Void> quickActionByPath(@PathVariable String businessType,
                                          @PathVariable String businessId,
                                          @RequestBody @Valid EmbeddedApprovalActionDTO dto) {
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        LoginUser u = AuthContext.getCurrentOrNull();
        if (dto.getUserId() == null && u != null) {
            dto.setUserId(u.getUserId());
        }
        if (dto.getUserName() == null && u != null) {
            dto.setUserName(u.getUsername());
        }
        embeddedApprovalService.quickAction(dto);
        return BaseResponse.success();
    }
}
