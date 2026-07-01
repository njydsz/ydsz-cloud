package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.WarrantyCreateDTO;
import com.njydsz.pmis.execution.dto.WarrantyTerminateDTO;
import com.njydsz.pmis.execution.entity.WarrantyDO;
import com.njydsz.pmis.execution.service.WarrantyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/execution/warranty")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService service;

    @Operation(summary = "创建质保期")
    @PostMapping
    public R<Long> create(@Valid @RequestBody WarrantyCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "手动提前终止质保期")
    @PostMapping("/terminate")
    public R<Void> terminate(@Valid @RequestBody WarrantyTerminateDTO dto) {
        service.terminate(dto);
        return R.ok();
    }

    @Operation(summary = "扫描即将到期（≤ today + noticeDays 天）")
    @PostMapping("/scan/expiring")
    public R<Integer> scanExpiring(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
            @RequestParam(defaultValue = "30") int noticeDays) {
        return R.ok(service.scanExpiring(today, noticeDays));
    }

    @Operation(summary = "扫描已过期")
    @PostMapping("/scan/overdue")
    public R<Integer> scanOverdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return R.ok(service.scanOverdue(today));
    }

    @Operation(summary = "即将到期列表")
    @GetMapping("/expiring")
    public R<List<WarrantyDO>> listExpiring(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return R.ok(service.listExpiring(until));
    }

    @Operation(summary = "质保期分页")
    @GetMapping("/page")
    public R<PageResult<WarrantyDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String keyword) {
        return R.ok(PageResult.ofPage(service.page(page, size, status, initiationId, keyword)));
    }

    @Operation(summary = "质保期详情")
    @GetMapping("/{id}")
    public R<WarrantyDO> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }
}
