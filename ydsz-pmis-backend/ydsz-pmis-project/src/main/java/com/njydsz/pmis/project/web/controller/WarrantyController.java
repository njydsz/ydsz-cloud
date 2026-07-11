package com.njydsz.pmis.project.web.controller.aftersales;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.domain.dto.WarrantyCreateDTO;
import com.njydsz.pmis.project.domain.dto.WarrantyTerminateDTO;
import com.njydsz.pmis.project.domain.entity.WarrantyDO;
import com.njydsz.pmis.project.server.service.WarrantyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 质保期 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "项目质保期管理")
@RestController
@RequestMapping("/afterSales/warranty")
@RequiredArgsConstructor
@Validated
public class WarrantyController {

    /** 保修服务 */
    private final WarrantyService service;

    @Operation(summary = "创建质保期")
    @PrePermission("aftersales:warranty:create")
    @Idempotent(key = "warranty:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody WarrantyCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "手动提前终止质保期")
    @PrePermission("aftersales:warranty:terminate")
    @Idempotent(key = "warranty:update", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/terminate")
    public Result<Void> terminate(@Valid @RequestBody WarrantyTerminateDTO dto) {
        service.terminate(dto);
        return Result.ok();
    }

    @Operation(summary = "扫描即将到期（≤ today + noticeDays 天）")
    @PrePermission("aftersales:warranty:scan")
    @Idempotent(key = "warranty:scanExpiring", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/scan/expiring")
    public Result<Integer> scanExpiring(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
            @RequestParam(defaultValue = "30") int noticeDays) {
        return Result.ok(service.scanExpiring(today, noticeDays));
    }

    @Operation(summary = "扫描已过期")
    @PrePermission("aftersales:warranty:scan")
    @Idempotent(key = "warranty:scanOverdue", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/scan/overdue")
    public Result<Integer> scanOverdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return Result.ok(service.scanOverdue(today));
    }

    @Operation(summary = "即将到期列表")
    @PrePermission("aftersales:warranty:list")
    @GetMapping("/expiring")
    public Result<List<WarrantyDO>> listExpiring(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return Result.ok(service.listExpiring(until));
    }

    @Operation(summary = "质保期分页")
    @PrePermission("aftersales:warranty:list")
    @GetMapping("/page")
    public Result<PageResult<WarrantyDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(PageResult.ofPage(service.page(page, size, status, initiationId, keyword)));
    }

    @Operation(summary = "质保期详情")
    @PrePermission("aftersales:warranty:list")
    @GetMapping("/{id}")
    public Result<WarrantyDO> getById(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }
}
