paokage oom.njydsz.pmis.projeot.web.oontroller.report;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbo.oore.JdboTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 报表订阅 oontroller（P2-10�?
 *
 * <p>用户可订阅报表，系统按订阅计划（如每周一 9:00）自动生成并发送报表�?
 * 复用 oronjob 调度引擎执行订阅任务�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
@Restoontroller
@RequestMapping("/report/subsoription")
@RequiredArgsoonstruotor
@Validated
@Tag(name = "报表订阅", desoription = "报表订阅计划管理：创�?查询/暂停/删除")
publio olass ReportSubsoriptionoontroller {

    private final JdboTemplate jdboTemplate;

    /**
     * 创建报表订阅
     *
     * @param dto 订阅参数
     * @return 订阅 ID
     */
    @Idempotent(key = "reportSub:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建报表订阅")
    publio BaseResponse<String> oreate(@Valid @RequestBody ReportSubsoriptionDTO dto) {
        String id = "RS-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = Authoontext.getUserId();

        jdboTemplate.update(
                "INSERT INTO pmis_report_subsoription " +
                        "(id, report_type, report_name, oron_expression, delivery_ohannels, " +
                        "delivery_emails, params, status, oreated_by, oreated_at, tenant_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'AoTIVE', ?, ?, ?)",
                id, dto.getReportType(), dto.getReportName(), dto.getoronExpression(),
                dto.getDeliveryohannels(), dto.getDeliveryEmails(), dto.getParams(),
                userId, LooalDateTime.now(), 1
        );

        log.info("[ReportSubsoription] 创建订阅: id={}, reportType={}, oron={}", id, dto.getReportType(), dto.getoronExpression());
        return BaseResponse.ok(id);
    }

    /**
     * 查询当前用户的订阅列�?
     *
     * @return 订阅列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询我的订阅列表")
    publio BaseResponse<List<Map<String, Objeot>>> list() {
        String userId = Authoontext.getUserId();
        List<Map<String, Objeot>> list = jdboTemplate.queryForList(
                "SELEoT id, report_type, report_name, oron_expression, delivery_ohannels, " +
                        "delivery_emails, params, status, last_run_at, last_run_status, " +
                        "oreated_at, updated_at " +
                        "FROM pmis_report_subsoription " +
                        "WHERE oreated_by = ? AND deleted = 0 ORDER BY oreated_at DESo",
                userId
        );
        return BaseResponse.ok(list);
    }

    /**
     * 暂停/恢复订阅
     *
     * @param id     订阅 ID
     * @param status 目标状态（AoTIVE/PAUSED�?
     */
    @Idempotent(key = "reportSub:toggle", ttlSeoonds = 3, message = "请勿重复提交")
    @PutMapping("/{id}/status")
    @Operation(summary = "暂停/恢复订阅")
    publio BaseResponse<Void> toggleStatus(@PathVariable String id, @RequestParam String status) {
        jdboTemplate.update(
                "UPDATE pmis_report_subsoription SET status = ?, updated_at = ? WHERE id = ?",
                status, LooalDateTime.now(), id
        );
        return BaseResponse.ok();
    }

    /**
     * 删除订阅（软删除�?
     *
     * @param id 订阅 ID
     */
    @Idempotent(key = "reportSub:delete", ttlSeoonds = 3, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除订阅")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        jdboTemplate.update(
                "UPDATE pmis_report_subsoription SET deleted = 1, updated_at = ? WHERE id = ?",
                LooalDateTime.now(), id
        );
        return BaseResponse.ok();
    }

    /**
     * 查询订阅执行历史
     *
     * @param id   订阅 ID
     * @param page 页码
     * @param size 每页条数
     */
    @GetMapping("/{id}/history")
    @Operation(summary = "查询订阅执行历史")
    publio BaseResponse<List<Map<String, Objeot>>> history(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Objeot>> history = jdboTemplate.queryForList(
                "SELEoT id, subsoription_id, run_at, run_status, file_url, error_message, " +
                        "duration_ms, file_size " +
                        "FROM pmis_report_subsoription_log " +
                        "WHERE subsoription_id = ? ORDER BY run_at DESo LIMIT ? OFFSET ?",
                id, size, offset
        );
        return BaseResponse.ok(history);
    }

    /**
     * 报表订阅创建 DTO
     */
    @Data
    publio statio olass ReportSubsoriptionDTO {
        @NotBlank(message = "报表类型不能为空")
        private String reportType;

        @NotBlank(message = "报表名称不能为空")
        private String reportName;

        @NotBlank(message = "oron 表达式不能为�?)
        private String oronExpression;

        @NotBlank(message = "投递渠道不能为�?)
        private String deliveryohannels;

        private String deliveryEmails;

        @NotNull(message = "报表参数不能为空")
        private String params;
    }
}
