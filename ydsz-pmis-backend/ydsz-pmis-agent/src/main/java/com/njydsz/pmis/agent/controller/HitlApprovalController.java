package com.njydsz.pmis.agent.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.HitlApprovalActionDTO;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.entity.HitlApprovalRequestDO;
import com.njydsz.pmis.agent.hitl.HitlApprovalService;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HITL 人工审批 Controller（P3-4 落地）
 *
 * <p>提供审批请求的查询、批准、拒绝、取消接口，对标 LangGraph interrupt / Dify Human Feedback。
 *
 * <p>权限码：
 * <ul>
 *   <li>{@code agent:hitl:list} - 查询审批列表 / 详情</li>
 *   <li>{@code agent:hitl:approve} - 批准 / 拒绝 / 取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Slf4j
@Tag(name = "AI 智能体 - 人工审批")
@RestController
@RequestMapping("/agent/hitl/approvals")
@Validated
public class HitlApprovalController {

    /** HITL 人工审批服务 */
    private final HitlApprovalService service;

    public HitlApprovalController(HitlApprovalService service) {
        this.service = service;
    }

    /**
     * 分页查询审批请求。
     *
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @param status    审批状态（可空）
     * @param agentType Agent 类型（可空）
     * @param bizType   关联业务类型（可空）
     * @param bizId     关联业务 ID（可空）
     * @return 分页结果
     */
    @Operation(summary = "分页查询审批请求")
    @PrePermission("agent:hitl:list")
    @GetMapping("/page")
    public Result<Page<HitlApprovalRequestDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId) {
        return Result.ok(service.page(page, size, status, agentType, bizType, bizId));
    }

    /**
     * 查询待审批请求列表。
     *
     * @param limit 返回条数，默认 20
     * @return 待审批请求列表
     */
    @Operation(summary = "待审批请求列表")
    @PrePermission("agent:hitl:list")
    @GetMapping("/pending")
    public Result<List<HitlApprovalRequestDO>> pending(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Result.ok(service.listPending(limit));
    }

    /**
     * 查询审批请求详情。
     *
     * @param id 审批请求 ID
     * @return 审批请求详情
     */
    @Operation(summary = "审批请求详情")
    @PrePermission("agent:hitl:list")
    @GetMapping("/{id}")
    public Result<HitlApprovalRequestDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 批准审批请求。
     *
     * @param id  审批请求 ID
     * @param dto 审批动作 DTO（含审批人信息与备注）
     * @return ReAct 执行结果
     */
    @Operation(summary = "批准审批请求")
    @PrePermission("agent:hitl:approve")
    @Idempotent(key = "hitl-approval:approve", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/approve")
    public Result<ReActResult> approve(@PathVariable String id,
                                       @Valid @RequestBody HitlApprovalActionDTO dto) {
        return Result.ok(service.approve(id, dto.getApproverId(), dto.getApproverName(), dto.getComment()));
    }

    /**
     * 拒绝审批请求。
     *
     * @param id  审批请求 ID
     * @param dto 审批动作 DTO（含审批人信息与备注）
     * @return ReAct 执行结果
     */
    @Operation(summary = "拒绝审批请求")
    @PrePermission("agent:hitl:approve")
    @Idempotent(key = "hitl-approval:reject", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/reject")
    public Result<ReActResult> reject(@PathVariable String id,
                                      @Valid @RequestBody HitlApprovalActionDTO dto) {
        return Result.ok(service.reject(id, dto.getApproverId(), dto.getApproverName(), dto.getComment()));
    }

    /**
     * 取消审批请求。
     *
     * @param id  审批请求 ID
     * @param dto 审批动作 DTO（含审批人信息与备注）
     * @return 空结果
     */
    @Operation(summary = "取消审批请求")
    @PrePermission("agent:hitl:approve")
    @Idempotent(key = "hitl-approval:cancel", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable String id,
                               @Valid @RequestBody HitlApprovalActionDTO dto) {
        service.cancel(id, dto.getApproverId(), dto.getApproverName(), dto.getComment());
        return Result.ok();
    }
}
