package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.BenchRecordCreateDTO;
import com.njydsz.pmis.user.entity.BenchRecordDO;
import com.njydsz.pmis.user.service.BenchService;
import com.njydsz.pmis.user.service.impl.BenchServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bench 闲置池 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "Bench 闲置池管理")
@RestController
@RequestMapping("/api/v1/bench")
@RequiredArgsConstructor
public class BenchController {

    private final BenchService benchService;

    @Operation(summary = "入池 / 出池 业务动作")
    @PrePermission("resource:bench:act")
    @OperationLog(module = "Bench 池", action = "入/出池", bizType = "BENCH_RECORD")
    @PostMapping("/act")
    public R<Long> act(@Valid @RequestBody BenchRecordCreateDTO dto) {
        return R.ok(benchService.act(dto));
    }

    @Operation(summary = "Bench 详情")
    @GetMapping("/{id}")
    public R<BenchRecordDO> get(@PathVariable Long id) {
        return R.ok(benchService.getById(id));
    }

    @Operation(summary = "员工当前 Bench 记录")
    @GetMapping("/active/{employeeId}")
    public R<BenchRecordDO> getActiveByEmployee(@PathVariable Long employeeId) {
        return R.ok(benchService.getActiveByEmployee(employeeId));
    }

    @Operation(summary = "按池汇总")
    @GetMapping("/aggregate/by-pool")
    public R<List<Map<String, Object>>> aggregateByPool() {
        return R.ok(benchService.aggregateByPool());
    }

    @Operation(summary = "流动统计（按日期区间）")
    @GetMapping("/flow")
    public R<List<Map<String, Object>>> flowByDateRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(benchService.flowByDateRange(from, to));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<BenchRecordDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long poolId,
            @RequestParam(required = false) String status) {
        return R.ok(benchService.page(page, size, poolId, status));
    }

    @Operation(summary = "累计闲置成本")
    @GetMapping("/total-idle-cost")
    public R<BigDecimal> totalIdleCost() {
        return R.ok(benchService.totalIdleCost());
    }

    @Operation(summary = "Bench 仪表盘汇总")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        if (benchService instanceof BenchServiceImpl impl) {
            return R.ok(impl.dashboard());
        }
        Map<String, Object> out = new HashMap<>();
        out.put("activePools", benchService.aggregateByPool());
        out.put("totalIdleCost", benchService.totalIdleCost());
        return R.ok(out);
    }
}
