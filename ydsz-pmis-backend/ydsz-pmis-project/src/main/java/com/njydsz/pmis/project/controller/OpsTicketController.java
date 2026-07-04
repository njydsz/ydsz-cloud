package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.OpsTicketAssignDTO;
import com.njydsz.pmis.project.dto.OpsTicketCreateDTO;
import com.njydsz.pmis.project.dto.OpsTicketStatusDTO;
import com.njydsz.pmis.project.entity.OpsTicketDO;
import com.njydsz.pmis.project.service.OpsTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运维工单 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "运维工单管理")
@RestController
@RequestMapping("/api/v1/execution/ops-ticket")
@RequiredArgsConstructor
@Validated
public class OpsTicketController {

    private final OpsTicketService service;

    @Operation(summary = "创建工单")
    @PrePermission("aftersales:ops-ticket:create")
    @Idempotent(key = "ops-ticket:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OpsTicketCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "派单")
    @PrePermission("aftersales:ops-ticket:assign")
    @Idempotent(key = "ops-ticket:update", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/assign")
    public Result<Void> assign(@Valid @RequestBody OpsTicketAssignDTO dto) {
        service.assign(dto);
        return Result.ok();
    }

    @Operation(summary = "状态变更")
    @PrePermission("aftersales:ops-ticket:status")
    @PostMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody OpsTicketStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "关闭工单并评价")
    @PrePermission("aftersales:ops-ticket:evaluate")
    @PostMapping("/close-evaluate")
    public Result<Void> closeAndEvaluate(@Valid @RequestBody OpsTicketStatusDTO dto) {
        service.closeAndEvaluate(dto);
        return Result.ok();
    }

    @Operation(summary = "SLA 扫描")
    @PrePermission("aftersales:ops-ticket:scan")
    @PostMapping("/scan/sla")
    public Result<Integer> scanSla() {
        return Result.ok(service.scanSlaBreaches());
    }

    @Operation(summary = "工单分页")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/page")
    public Result<PageResult<OpsTicketDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(PageResult.ofPage(service.page(page, size, status, priority,
                initiationId, assigneeId, keyword)));
    }

    @Operation(summary = "SLA 达成率")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/sla-summary")
    public Result<List<Map<String, Object>>> slaSummary() {
        return Result.ok(service.slaSummary());
    }

    @Operation(summary = "按状态聚合")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/aggregate/status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) Long initiationId) {
        return Result.ok(service.aggregateByStatus(initiationId));
    }

    @Operation(summary = "工单详情")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/{id}")
    public Result<OpsTicketDO> getById(@PathVariable @Min(1) Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "按项目查询工单")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/by-initiation/{initiationId}")
    public Result<List<OpsTicketDO>> listByInitiation(@PathVariable @Min(1) Long initiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }
}
