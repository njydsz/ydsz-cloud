package com.njydsz.workflow.web.controller.delegate;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.dto.FlowDelegateAuthSaveDTO;
import com.njydsz.workflow.domain.entity.FlowDelegateAuth;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.domain.dto.post.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.dto.put.FlowDelegateAuthPutDTO;

/**
 * 长期授权委派 Controller（P1-4）
 *
 * <p>提供工作流「长期授权委派」HTTP API，对标钉钉/飞书审批"审批委托"模块。
 * 与短期一次性转办（{@code FlowTaskController.transfer}）不同，长期委派用于
 * 员工<b>休假 / 出差 / 离职过渡期</b>的持续性代理。
 *
 * <p><b>业务示例：</b>用户 A 休假 7 天，希望 B 代理处理所有流程
 * <pre>
 * {
 *   "ownerUserId": 1001,
 *   "ownerUserName": "张三",
 *   "delegateUserId": 1002,
 *   "delegateUserName": "李四",
 *   "scopeType": "ALL",
 *   "startTime": "2026-07-02T00:00:00",
 *   "endTime": "2026-07-09T23:59:59",
 *   "reason": "年假"
 * }
 * </pre>
 *
 * <p><b>委派范围（scopeType）：</b>
 * <ul>
 *   <li><b>ALL</b>：所有流程（默认）</li>
 *   <li><b>FLOW</b>：指定流程（需传 {@code flowCode}）</li>
 *   <li><b>NODE</b>：指定流程 + 节点（需传 {@code flowCode} + {@code nodeCode}）</li>
 *   <li><b>ROLE</b>：指定角色（需传 {@code roleCode}）</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/engine/delegateAuth/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>创建 / 撤回</b>：{@code POST /delegateAuth/create} / {@code POST .../revoke}</li>
 *   <li><b>启停控制</b>：{@code POST /delegateAuth/{id}/status}</li>
 *   <li><b>列表查询</b>：{@code GET /mine}（我设置的）/ {@code GET /asDelegate}（代理给我的）</li>
 *   <li><b>日志查询</b>：{@code GET /log/delegate}（我代理处理了哪些）/ {@code GET /log/owner}（我的哪些被代理）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>写接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_DELEGATE_MANAGE} 权限码；读接口基于
 * SecurityContext 自动按 userId 过滤。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重（避免双击创建重复授权）</li>
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>撤销操作要求 ownerUserId == 当前用户（防越权）</li>
 *   <li>授权时间窗口由 Service 层校验（startTime &lt; endTime + 未过期）</li>
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、权限校验、DTO 转换；
 * 时间窗口校验、状态机、委派日志写入下沉到 {@link FlowDelegateAuthService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDelegateAuthService 长期授权委派服务
 * @see FlowDelegateAuth 长期授权实体
 * @see FlowDelegateAuthPostDTO 创建授权 DTO
 * @see FlowDelegateAuthPutDTO 更新授权 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-delegate", description = "工作流授权委派接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDelegateController {

    /** P1-4: 长期授权委派服务 */
    private final FlowDelegateAuthService delegateAuthService;

    /**
     * P1-4: 创建长期授权委派
     *
     * <p>业务示例：用户 A 休假 7 天，希望 B 代理处理所有流程。
     * 提交时 body 形如：
     * <pre>
     * {
     *   "ownerUserId": 1001,
     *   "ownerUserName": "张三",
     *   "delegateUserId": 1002,
     *   "delegateUserName": "李四",
     *   "scopeType": "ALL",
     *   "startTime": "2026-07-02T00:00:00",
     *   "endTime": "2026-07-09T23:59:59",
     *   "reason": "年假"
     * }
     * </pre>
     */
    @Idempotent(key = "ydsz:workflow:FlowDelegateController:createDelegateAuth:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdelegate.createDelegateAuth", threshold = 50)
    @PostMapping("/delegateAuth/create")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public BaseResponse<String> createDelegateAuth(@Valid @RequestBody FlowDelegateAuthPostDTO dto) {
        FlowDelegateAuth auth = WorkflowConverter.INSTANT.saveDtoToEntity(dto);
        // 从 SecurityContext 兜底 ownerUserId（防止前端漏传）
        if (auth.getOwnerUserId() == null) {
            auth.setOwnerUserId(AuthContext.getUserId());
        }
        String id = delegateAuthService.create(auth);
        return BaseResponse.success(id);
    }

    /**
     * P1-4: 撤回授权。
     *
     * @param id 授权记录 ID
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowDelegateController:revokeDelegateAuth:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdelegate.revokeDelegateAuth", threshold = 50)
    @PostMapping("/delegateAuth/{id}/revoke")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public BaseResponse<Void> revokeDelegateAuth(@PathVariable String id) {
        String ownerId = AuthContext.getUserId();
        delegateAuthService.revoke(id, ownerId);
        return BaseResponse.success();
    }

    /**
     * P1-4: 启用/停用授权。
     *
     * @param id     授权记录 ID
     * @param status 目标状态
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowDelegateController:updateDelegateAuthStatus:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdelegate.updateDelegateAuthStatus", threshold = 50)
    @PostMapping("/delegateAuth/{id}/status")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public BaseResponse<Void> updateDelegateAuthStatus(@PathVariable String id,
                                                 @RequestParam String status) {
        String operatorId = AuthContext.getUserId();
        delegateAuthService.updateStatus(id, status, operatorId);
        return BaseResponse.success();
    }

    /**
     * P1-4: 查"我设置的"授权列表。
     *
     * @param status 状态筛选（可选）
     * @return 授权列表
     */
    @GetMapping("/delegateAuth/mine")
    public BaseResponse<List<FlowDelegateAuthVO>> listMyDelegateAuths(
            @RequestParam(required = false) String status) {
        String ownerId = AuthContext.getUserId();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowDelegateAuthListToVO(delegateAuthService.listMine(ownerId, tenantId, status)));
    }

    /**
     * P1-4: 查"代理给我的"授权列表。
     *
     * @param status 状态筛选（可选）
     * @return 授权列表
     */
    @GetMapping("/delegateAuth/asDelegate")
    public BaseResponse<List<FlowDelegateAuthVO>> listAsDelegate(
            @RequestParam(required = false) String status) {
        String delegateUserId = AuthContext.getUserId();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowDelegateAuthListToVO(delegateAuthService.listAsDelegate(delegateUserId, tenantId, status)));
    }

    /**
     * P1-4: 查"我代理处理了哪些任务"。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 委派处理日志分页
     */
    @GetMapping("/delegateAuth/log/delegate")
    public BaseResponse<PageResponse<?>> myDelegateLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String delegateUserId = AuthContext.getUserId();
        return BaseResponse.success(delegateAuthService.listDelegateLog(delegateUserId, page, size));
    }

    /**
     * P1-4: 查"我的哪些任务被代理了"。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 被代理任务日志分页
     */
    @GetMapping("/delegateAuth/log/owner")
    public BaseResponse<PageResponse<?>> myOwnerLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String ownerUserId = AuthContext.getUserId();
        return BaseResponse.success(delegateAuthService.listOwnerLog(ownerUserId, page, size));
    }
    /**
     * 将 PostDTO 转换为 SaveDTO（统一内部使用的保存形态）。
     *
     * <p>PostDTO 是面向前端的"创建请求"形态，SaveDTO 是 Service 层内部统一的
     * "保存"形态。本方法做字段对齐（owner / delegate / scope / flow / node / role / 时间 / 原因）。
     * <p>当前 controller 主路径直接走 {@code WorkflowConverter} 完成转换；本方法为
     * 兼容性保留的 fallback，供未来切换转换器或外部调用。
     *
     * @param dto 前端传入的 PostDTO
     * @return 内部统一的 SaveDTO
     */
    private FlowDelegateAuthSaveDTO toSaveDTO(FlowDelegateAuthPostDTO dto) {
        FlowDelegateAuthSaveDTO saveDTO = new FlowDelegateAuthSaveDTO();
        saveDTO.setOwnerUserId(dto.getOwnerUserId());
        saveDTO.setOwnerUserName(dto.getOwnerUserName());
        saveDTO.setDelegateUserId(dto.getDelegateUserId());
        saveDTO.setDelegateUserName(dto.getDelegateUserName());
        saveDTO.setScopeType(dto.getScopeType());
        saveDTO.setFlowCode(dto.getFlowCode());
        saveDTO.setNodeCode(dto.getNodeCode());
        saveDTO.setRoleCode(dto.getRoleCode());
        saveDTO.setStartTime(dto.getStartTime());
        saveDTO.setEndTime(dto.getEndTime());
        saveDTO.setReason(dto.getReason());
        return saveDTO;
    }

    /**
     * 将 PutDTO 转换为 SaveDTO（用于更新场景的形态对齐）。
     *
     * <p>PutDTO 是面向前端的"更新请求"形态，与 PostDTO 字段一致但语义不同。
     * 本方法保持二者与 SaveDTO 的字段映射同步，避免字段遗漏。
     *
     * @param dto 前端传入的 PutDTO
     * @return 内部统一的 SaveDTO
     */
    private FlowDelegateAuthSaveDTO toSaveDTO(FlowDelegateAuthPutDTO dto) {
        FlowDelegateAuthSaveDTO saveDTO = new FlowDelegateAuthSaveDTO();
        saveDTO.setOwnerUserId(dto.getOwnerUserId());
        saveDTO.setOwnerUserName(dto.getOwnerUserName());
        saveDTO.setDelegateUserId(dto.getDelegateUserId());
        saveDTO.setDelegateUserName(dto.getDelegateUserName());
        saveDTO.setScopeType(dto.getScopeType());
        saveDTO.setFlowCode(dto.getFlowCode());
        saveDTO.setNodeCode(dto.getNodeCode());
        saveDTO.setRoleCode(dto.getRoleCode());
        saveDTO.setStartTime(dto.getStartTime());
        saveDTO.setEndTime(dto.getEndTime());
        saveDTO.setReason(dto.getReason());
        return saveDTO;
    }
}
