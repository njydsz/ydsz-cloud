package com.njydsz.project.web.controller.execution;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.server.engine.BudgetGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 预算强管控查询 Controller
 * <p>
 * 用于前端展示项目预算占用率与告警级别。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "预算强管控")
@RestController
@RequestMapping("/api/project/execution/budget")
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
    @AuthApiPermission(apiCodes = "execution:budget:view")
    @GetMapping("/occupancy")
    public BaseResponse<Map<String, Object>> occupancy(@RequestParam String initiationId) {
        return BaseResponse.ok(budgetGuard.occupancy(initiationId));
    }
}
