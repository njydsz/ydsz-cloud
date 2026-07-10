package com.njydsz.pmis.workflow.controller.integration;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.integration.EmbeddedApprovalActionDTO;
import com.njydsz.pmis.workflow.dto.integration.EmbeddedApprovalViewDTO;
import com.njydsz.pmis.workflow.service.integration.FlowEmbeddedApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P2-2 嵌入式审批 Controller
 *
 * <p>业务页（项目立项/合同/工时/采购等）通过本 Controller 直接拉取嵌入式审批面板数据，
 * 业务侧不需要感知 taskId 即可完成"查看/通过/驳回/转办/催办/撤回"。
 *
 * <p>与 FlowEngineController 的区别：
 * <ul>
 *   <li>FlowEngineController：管理端/审批中心，按 taskId 操作，提供完整能力</li>
 *   <li>FlowEmbeddedApprovalController：业务端，按 businessType+businessId 操作，仅暴露嵌入式场景所需最小集</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "嵌入式审批")
@RestController
@RequestMapping("/workflow/embedded")
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
    public Result<EmbeddedApprovalViewDTO> loadPanel(@RequestParam String businessType,
                                                     @RequestParam String businessId,
                                                     @RequestParam(required = false) String userId) {
        String uid = userId;
        if (uid == null) {
            LoginUser u = SecurityContext.getCurrentOrNull();
            if (u != null) {
                uid = u.getUserId();
            }
        }
        if (uid == null) {
            return Result.failed(BizErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.ok(embeddedApprovalService.loadPanel(businessType, businessId, uid));
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
    @Idempotent(key = "flow-embedded-approval:quick-action", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/action")
    public Result<Void> quickAction(@Valid @RequestBody EmbeddedApprovalActionDTO dto) {
        LoginUser u = SecurityContext.getCurrentOrNull();
        if (dto.getUserId() == null && u != null) {
            dto.setUserId(u.getUserId());
        }
        if (dto.getUserName() == null && u != null) {
            dto.setUserName(u.getUsername());
        }
        embeddedApprovalService.quickAction(dto);
        return Result.ok();
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
    @Idempotent(key = "flow-embedded-approval:quick-action-by-path", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{businessType}/{businessId}/action")
    public Result<Void> quickActionByPath(@PathVariable String businessType,
                                          @PathVariable String businessId,
                                          @RequestBody @Valid EmbeddedApprovalActionDTO dto) {
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        LoginUser u = SecurityContext.getCurrentOrNull();
        if (dto.getUserId() == null && u != null) {
            dto.setUserId(u.getUserId());
        }
        if (dto.getUserName() == null && u != null) {
            dto.setUserName(u.getUsername());
        }
        embeddedApprovalService.quickAction(dto);
        return Result.ok();
    }
}
