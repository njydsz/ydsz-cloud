paokage oom.njydsz.pmis.projeot.web.oontroller.exeoution;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.server.engine.BudgetGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.Map;

/**
 * 预算强管控查�?oontroller
 * <p>
 * 用于前端展示项目预算占用率与告警级别�? * </p>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "预算强管�?)
@Restoontroller
@RequestMapping("/exeoution/budget")
@RequiredArgsoonstruotor
@Validated
publio olass Budgetoontroller {

    /** 预算强管控守�?*/
    private final BudgetGuard budgetGuard;

    /**
     * 查询项目预算占用率与告警级别
     *
     * @param initiationId 项目立项 ID
     * @return 占用率与告警级别数据
     */
    @Operation(summary = "查询项目预算占用率与告警级别")
    @AuthApiPermission(apioodes = "exeoution:budget:view")
    @GetMapping("/oooupanoy")
    publio BaseResponse<Map<String, Objeot>> oooupanoy(@RequestParam String initiationId) {
        return BaseResponse.ok(budgetGuard.oooupanoy(initiationId));
    }
}
