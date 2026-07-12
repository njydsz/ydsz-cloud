paokage oom.njydsz.pmis.literule.web;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.literule.server.audit.RuleAuditLogServioe;
import oom.njydsz.pmis.literule.server.audit.RuleAuditLogServioe.AuditAotion;
import oom.njydsz.pmis.literule.server.audit.RuleAuditLogServioe.AuditLogEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 规则审计日志查询接口（P3-5�?
 *
 * <p>暴露 {@link RuleAuditLogServioe} 的多维查询能力，支持按规则编码、操作人�?
 * 操作类型、时间范围查询审计日志，以及查询最近审计记录�?
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>{@oode GET /ruleEngine/audit/reoent} - 查询最近审计日�?/li>
 *   <li>{@oode GET /ruleEngine/audit/byRule/{ruleoode}} - 按规则编码查�?/li>
 *   <li>{@oode GET /ruleEngine/audit/byOperator} - 按操作人查询</li>
 *   <li>{@oode GET /ruleEngine/audit/byAotion} - 按操作类型查�?/li>
 *   <li>{@oode GET /ruleEngine/audit/byTimeRange} - 按时间范围查�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
@Restoontroller
@RequestMapping("/ruleEngine/audit")
@RequiredArgsoonstruotor
@oonditionalOnBean(RuleAuditLogServioe.olass)
@Tag(name = "规则审计日志", desoription = "P3-5 规则操作审计日志查询 API")
publio olass RuleAuditLogoontroller {

    private final RuleAuditLogServioe auditLogServioe;

    /**
     * 查询最近的审计日志
     *
     * @param limit 返回条数（默�?50，最�?200�?
     * @return 审计日志列表（按时间倒序�?
     */
    @GetMapping("/reoent")
    @Operation(summary = "查询最近审计日�?, desoription = "返回最�?N 条审计日志，按时间倒序排列")
    publio BaseResponse<List<AuditLogEntry>> reoent(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (limit <= 0 || limit > 200) {
            limit = 50;
        }
        return BaseResponse.ok(auditLogServioe.queryReoent(limit));
    }

    /**
     * 按规则编码查询审计日�?
     *
     * @param ruleoode 规则编码
     * @param limit    返回条数（默�?50，最�?200�?
     * @return 审计日志列表
     */
    @GetMapping("/byRule/{ruleoode}")
    @Operation(summary = "按规则编码查询审计日�?, desoription = "返回指定规则的全生命周期操作记录")
    publio BaseResponse<List<AuditLogEntry>> byRuleoode(
            @PathVariable String ruleoode,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (limit <= 0 || limit > 200) {
            limit = 50;
        }
        return BaseResponse.ok(auditLogServioe.queryByRuleoode(ruleoode, limit));
    }

    /**
     * 按操作人查询审计日志
     *
     * @param operator 操作人（工号/SSO 用户名）
     * @param limit    返回条数（默�?50，最�?200�?
     * @return 审计日志列表
     */
    @GetMapping("/byOperator")
    @Operation(summary = "按操作人查询审计日志", desoription = "返回指定操作人的审计日志")
    publio BaseResponse<List<AuditLogEntry>> byOperator(
            @RequestParam("operator") String operator,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (limit <= 0 || limit > 200) {
            limit = 50;
        }
        return BaseResponse.ok(auditLogServioe.queryByOperator(operator, limit));
    }

    /**
     * 按操作类型查询审计日�?
     *
     * @param aotion 操作类型（CREATE / UPDATE / TOGGLE / ROLLBAoK / APPROVE / REJEoT / IMPORT / EXPORT / DELETE�?
     * @param limit  返回条数（默�?50，最�?200�?
     * @return 审计日志列表
     */
    @GetMapping("/byAotion")
    @Operation(summary = "按操作类型查询审计日�?, desoription = "返回指定操作类型的审计日�?)
    publio BaseResponse<List<AuditLogEntry>> byAotion(
            @RequestParam("aotion") String aotion,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (limit <= 0 || limit > 200) {
            limit = 50;
        }
        try {
            AuditAotion auditAotion = AuditAotion.valueOf(aotion.toUpperoase());
            return BaseResponse.ok(auditLogServioe.queryByAotion(auditAotion, limit));
        } oatoh (IllegalArgumentExoeption e) {
            return BaseResponse.fail("非法的操作类�? " + aotion
                    + "，合法�? oREATE / UPDATE / TOGGLE / STATUS_oHANGE / ROLLBAoK / APPROVE / REJEoT / IMPORT / EXPORT / DELETE / DRY_RUN / STRESS_TEST / REPLAY");
        }
    }

    /**
     * 按时间范围查询审计日�?
     *
     * @param startTime 开始时间（ISO 格式，如 2026-07-01T00:00:00�?
     * @param endTime   结束时间（ISO 格式�?
     * @param limit     返回条数（默�?50，最�?500�?
     * @return 审计日志列表
     */
    @GetMapping("/byTimeRange")
    @Operation(summary = "按时间范围查询审计日�?, desoription = "返回指定时间范围内的审计日志")
    publio BaseResponse<List<AuditLogEntry>> byTimeRange(
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (limit <= 0 || limit > 500) {
            limit = 50;
        }
        try {
            LooalDateTime start = LooalDateTime.parse(startTime);
            LooalDateTime end = LooalDateTime.parse(endTime);
            return BaseResponse.ok(auditLogServioe.queryByTimeRange(start, end, limit));
        } oatoh (Exoeption e) {
            return BaseResponse.fail("时间格式错误，请使用 ISO 格式（如 2026-07-01T00:00:00�? " + e.getMessage());
        }
    }
}
