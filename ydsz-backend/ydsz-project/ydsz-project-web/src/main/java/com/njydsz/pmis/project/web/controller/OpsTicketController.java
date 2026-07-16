package com.njydsz.project.web.controller.aftersales;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.project.domain.dto.OpsTicketAssignDTO;
import com.njydsz.project.domain.dto.OpsTicketCreateDTO;
import com.njydsz.project.domain.dto.OpsTicketStatusDTO;
import com.njydsz.project.domain.entity.OpsTicketDO;
import com.njydsz.project.server.service.OpsTicketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 运维工单 Controller
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "运维工单管理")
@RestController
@RequestMapping("/api/project/afterSales/opsTicket")
@RequiredArgsConstructor
@Validated
public class OpsTicketController {

    /** 运维工单服务 */
    private final OpsTicketService service;

    @Operation(summary = "创建工单")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:create")
    @Idempotent(key = "opsTicket:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody OpsTicketCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    @Operation(summary = "派单")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:assign")
    @Idempotent(key = "opsTicket:update", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/assign")
    public BaseResponse<Void> assign(@Valid @RequestBody OpsTicketAssignDTO dto) {
        service.assign(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "状态变更")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:status")
    @Idempotent(key = "opsTicket:changeStatus", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody OpsTicketStatusDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "关闭工单并评价")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:evaluate")
    @Idempotent(key = "opsTicket:closeAndEvaluate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/closeEvaluate")
    public BaseResponse<Void> closeAndEvaluate(@Valid @RequestBody OpsTicketStatusDTO dto) {
        service.closeAndEvaluate(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "SLA 扫描")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:scan")
    @Idempotent(key = "opsTicket:scanSla", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/scan/sla")
    public BaseResponse<Integer> scanSla() {
        return BaseResponse.ok(service.scanSlaBreaches());
    }

    @Operation(summary = "工单分页")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:list")
    @GetMapping("/page")
    public BaseResponse<PageResponse<OpsTicketDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String keyword) {
        return BaseResponse.ok(PageResponse.ofPage(service.page(page, size, status, priority,
                initiationId, assigneeId, keyword)));
    }

    @Operation(summary = "SLA 达成率")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:list")
    @GetMapping("/slaSummary")
    public BaseResponse<List<Map<String, Object>>> slaSummary() {
        return BaseResponse.ok(service.slaSummary());
    }

    @Operation(summary = "按状态聚合")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:list")
    @GetMapping("/aggregate/status")
    public BaseResponse<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(service.aggregateByStatus(initiationId));
    }

    @Operation(summary = "工单详情")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:list")
    @GetMapping("/{id}")
    public BaseResponse<OpsTicketDO> getById(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    @Operation(summary = "按项目查询工单")
    @AuthApiPermission(apiCodes = "aftersales:opsTicket:list")
    @GetMapping("/byInitiation/{initiationId}")
    public BaseResponse<List<OpsTicketDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(service.listByInitiation(initiationId));
    }
}
