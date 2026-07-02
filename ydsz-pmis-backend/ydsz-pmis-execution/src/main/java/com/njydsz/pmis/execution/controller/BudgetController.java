package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.engine.BudgetGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 预算强管控查询 Controller
 * <p>
 * 用于前端展示项目预算占用率与告警级别。
 * </p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "预算强管控")
@RestController
@RequestMapping("/api/v1/execution/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetGuard budgetGuard;

    @Operation(summary = "查询项目预算占用率与告警级别")
    @PrePermission("execution:budget:view")
    @GetMapping("/occupancy")
    public R<Map<String, Object>> occupancy(@RequestParam Long initiationId) {
        return R.ok(budgetGuard.occupancy(initiationId));
    }
}
