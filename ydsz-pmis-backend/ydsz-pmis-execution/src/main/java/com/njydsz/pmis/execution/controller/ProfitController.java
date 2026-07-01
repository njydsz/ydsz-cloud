package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.ProfitSnapshotDTO;
import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import com.njydsz.pmis.execution.service.ProfitService;
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

@Tag(name = "利润核算")
@RestController
@RequestMapping("/api/v1/execution/profit")
@RequiredArgsConstructor
public class ProfitController {

    private final ProfitService service;

    @Operation(summary = "生成/更新项目月度利润快照")
    @PrePermission("execution:profit:snapshot")
    @PostMapping("/snapshot")
    public R<Long> snapshot(@Valid @RequestBody ProfitSnapshotDTO dto) {
        return R.ok(service.generateSnapshot(dto));
    }

    @Operation(summary = "查询项目某月快照")
    @PrePermission("execution:profit:list")
    @GetMapping("/snapshot")
    public R<ProfitSnapshotDO> get(@RequestParam Long initiationId, @RequestParam String period) {
        return R.ok(service.getByInitiationAndPeriod(initiationId, period));
    }

    @Operation(summary = "项目所有快照")
    @PrePermission("execution:profit:list")
    @GetMapping("/snapshots/{initiationId}")
    public R<List<ProfitSnapshotDO>> list(@PathVariable Long initiationId) {
        return R.ok(service.listByInitiation(initiationId));
    }

    @Operation(summary = "趋势")
    @GetMapping("/trend/{initiationId}")
    public R<List<Map<String, Object>>> trend(@PathVariable Long initiationId) {
        return R.ok(service.trendByPeriod(initiationId));
    }

    @Operation(summary = "项目健康度评分")
    @GetMapping("/health-score")
    public R<Integer> healthScore(@RequestParam Long initiationId, @RequestParam String period) {
        return R.ok(service.healthScore(initiationId, period));
    }
}
