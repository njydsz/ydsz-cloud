package com.njydsz.pmis.project.web.controller.execution;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.server.engine.BudgetGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/execution/budget")
@RequiredArgsConstructor
@Validated
public class BudgetController {

    /** 预算强管控守卫 */
    private final BudgetGuard budgetGuard;

    /**
     * 查询项目预算占用率与告警级别
     *
     * @param initiationId 项目立项 ID
     * @return 占用率与告警级别数据
     */
    @Operation(summary = "查询项目预算占用率与告警级别")
    @PrePermission("execution:budget:view")
    @GetMapping("/occupancy")
    public Result<Map<String, Object>> occupancy(@RequestParam String initiationId) {
        return Result.ok(budgetGuard.occupancy(initiationId));
    }
}
