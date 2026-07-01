package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.OpsTicketAssignDTO;
import com.njydsz.pmis.execution.dto.OpsTicketCreateDTO;
import com.njydsz.pmis.execution.dto.OpsTicketStatusDTO;
import com.njydsz.pmis.execution.entity.OpsTicketDO;
import com.njydsz.pmis.execution.service.OpsTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class OpsTicketController {

    private final OpsTicketService service;

    @Operation(summary = "创建工单")
    @PrePermission("aftersales:ops-ticket:create")
    @PostMapping
    public R<Long> create(@Valid @RequestBody OpsTicketCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "派单")
    @PrePermission("aftersales:ops-ticket:assign")
    @PostMapping("/assign")
    public R<Void> assign(@Valid @RequestBody OpsTicketAssignDTO dto) {
        service.assign(dto);
        return R.ok();
    }

    @Operation(summary = "状态变更")
    @PrePermission("aftersales:ops-ticket:status")
    @PostMapping("/status")
    public R<Void> changeStatus(@Valid @RequestBody OpsTicketStatusDTO dto) {
        service.changeStatus(dto);
        return R.ok();
    }

    @Operation(summary = "关闭工单并评价")
    @PrePermission("aftersales:ops-ticket:evaluate")
    @PostMapping("/close-evaluate")
    public R<Void> closeAndEvaluate(@Valid @RequestBody OpsTicketStatusDTO dto) {
        service.closeAndEvaluate(dto);
        return R.ok();
    }

    @Operation(summary = "SLA 扫描")
    @PrePermission("aftersales:ops-ticket:scan")
    @PostMapping("/scan/sla")
    public R<Integer> scanSla() {
        return R.ok(service.scanSlaBreaches());
    }

    @Operation(summary = "工单分页")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/page")
    public R<PageResult<OpsTicketDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword) {
        return R.ok(PageResult.ofPage(service.page(page, size, status, priority,
                initiationId, assigneeId, keyword)));
    }

    @Operation(summary = "SLA 达成率")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/sla-summary")
    public R<List<Map<String, Object>>> slaSummary() {
        return R.ok(service.slaSummary());
    }

    @Operation(summary = "按状态聚合")
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/aggregate/status")
    public R<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) Long initiationId) {
        return R.ok(service.aggregateByStatus(initiationId));
    }

    /** 工单详情 */
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/{id}")
    public R<OpsTicketDO> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    /** 按项目查询工单 */
    @PrePermission("aftersales:ops-ticket:list")
    @GetMapping("/by-initiation/{initiationId}")
    public R<List<OpsTicketDO>> listByInitiation(@PathVariable Long initiationId) {
        return R.ok(service.listByInitiation(initiationId));
    }
}
