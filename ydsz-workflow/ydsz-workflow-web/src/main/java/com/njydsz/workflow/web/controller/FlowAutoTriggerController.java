package com.njydsz.workflow.web.controller.integration;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.domain.dto.FlowAutoTriggerCreateDTO;
import com.njydsz.workflow.domain.entity.FlowAutoTrigger;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 流程自动触发规则 HTTP API
 *
 * <p>提供触发规则的 CRUD 管理接口，支持列表查询、创建、删除、启用/禁用切换。
 * 触发规则在流程实例完成时自动生效，无需手动调用。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/trigger}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>规则列表</b>：{@code GET /list} — 列出全部已注册规则（含启用/禁用状态）</li>
 *   <li><b>规则创建</b>：{@code POST /} — 注册新的触发规则（sourceFlow → targetFlow）</li>
 *   <li><b>规则删除</b>：{@code DELETE /{id}} — 注销规则（不可恢复，建议改用禁用）</li>
 *   <li><b>启停切换</b>：{@code PUT /{id}/toggle} — 启用或禁用规则</li>
 * </ul>
 *
 * <p><b>业务场景：</b>
 * <pre>
 *   流程A（合同审批） 完成 → 触发条件 (amount > 100000) → 自动启动流程B（财务复核）
 * </pre>
 *
 * <p>触发规则由 {@code FlowEventListener} 在源流程实例 COMPLETED 事件中异步加载并匹配，
 * 满足条件的目标流程由 {@code FlowAutoTriggerService.executeTrigger} 自动启动。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重（5s）</li>
 *   <li>写接口启用 {@link RateLimit} 限流 50 QPS</li>
 *   <li>条件表达式使用 QLExpress 沙箱执行，禁止访问外部资源</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.FlowAutoTriggerService 触发规则 Service
 * @see com.njydsz.workflow.server.engine.FlowEventListener 事件监听器（消费者）
 */
@Slf4j
@Tag(name = "流程自动触发规则")
@RestController
@RequestMapping("/api/v1/workflow/trigger")
@RequiredArgsConstructor
@Validated
public class FlowAutoTriggerController {

    /** 流程自动触发规则服务，负责规则注册、删除与启用/禁用管理 */
    private final FlowAutoTriggerService autoTriggerService;

    /**
     * 列出所有触发规则
     *
     * <p>返回全部已注册规则（启用 + 禁用），按创建时间倒序排列。
     * <p>典型场景：触发规则管理页加载列表。
     *
     * @return 触发规则列表（含 sourceFlowCode / targetFlowCode / conditionExpression / enabled）
     */
    @Operation(summary = "列出所有触发规则")
    @GetMapping("/list")
    public BaseResponse<List<FlowAutoTriggerVO>> list() {
        return BaseResponse.success(WorkflowConverter.INSTANT.flowAutoTriggerListToVO(autoTriggerService.listAll()));
    }

    /**
     * 创建触发规则
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>注册一条 (源流程 → 目标流程) 触发规则，条件表达式使用 QLExpress 沙箱。
     * <p>创建后立即生效（{@code enabled=true}），源流程 COMPLETED 时将异步触发。
     *
     * @param dto 触发规则 DTO（sourceFlowCode / targetFlowCode / conditionExpression / description）
     * @return 空响应
     */
    @Operation(summary = "创建触发规则")
    @Idempotent(key = "ydsz:workflow:FlowAutoTriggerController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowautotrigger.create", threshold = 50)
    @PostMapping
    @Audit(module = "自动触发", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    public BaseResponse<Void> create(@Valid @RequestBody FlowAutoTriggerCreateDTO dto) {
        String sourceFlowCode = dto.getSourceFlowCode();
        String targetFlowCode = dto.getTargetFlowCode();
        String conditionExpression = dto.getConditionExpression();
        autoTriggerService.registerTrigger(sourceFlowCode, targetFlowCode, conditionExpression);
        return BaseResponse.success();
    }

    /**
     * 删除触发规则
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p><b>物理删除</b>，不可恢复。如需临时停用建议改用 {@link #toggle} 切换状态。
     *
     * @param id 规则 ID
     * @return 空响应
     */
    @Operation(summary = "删除触发规则")
    @Idempotent(key = "ydsz:workflow:FlowAutoTriggerController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowautotrigger.delete", threshold = 50)
    @DeleteMapping("/{id}")
    @Audit(module = "自动触发", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    public BaseResponse<Void> delete(@PathVariable String id) {
        autoTriggerService.deleteById(id);
        return BaseResponse.success();
    }

    /**
     * 启用/禁用触发规则
     *
     * <p>幂等保护 5 秒。
     * <p>在 {@code enabled=true} 和 {@code enabled=false} 之间切换，<b>不删除规则</b>。
     * 禁用后源流程 COMPLETED 时将不再触发。
     *
     * @param id 规则 ID
     * @return 切换后的状态（id / enabled）
     */
    @Operation(summary = "启用/禁用触发规则")
    @Idempotent(key = "ydsz:workflow:FlowAutoTriggerController:toggle:lock", ttlSeconds = 5)
    @PutMapping("/{id}/toggle")
    public BaseResponse<Map<String, Object>> toggle(@PathVariable String id) {
        boolean enabled = autoTriggerService.toggleEnabled(id);
        return BaseResponse.success(Map.of("id", id, "enabled", enabled));
    }
}