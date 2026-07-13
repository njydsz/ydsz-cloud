package com.njydsz.pmis.project.web.controller.report;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报表订阅 Controller（P2-10）
 *
 * <p>用户可订阅报表，系统按订阅计划（如每周一 9:00）自动生成并发送报表。
 * 复用 cronjob 调度引擎执行订阅任务。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/report/subscription")
@RequiredArgsConstructor
@Validated
@Tag(name = "报表订阅", description = "报表订阅计划管理：创建/查询/暂停/删除")
public class ReportSubscriptionController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建报表订阅
     *
     * @param dto 订阅参数
     * @return 订阅 ID
     */
    @Idempotent(key = "reportSub:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建报表订阅")
    public BaseResponse<String> create(@Valid @RequestBody ReportSubscriptionDTO dto) {
        String id = "RS-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = AuthContext.getUserId();

        jdbcTemplate.update(
                "INSERT INTO pmis_report_subscription " +
                        "(id, report_type, report_name, cron_expression, delivery_channels, " +
                        "delivery_emails, params, status, created_by, created_at, tenant_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?, ?, ?)",
                id, dto.getReportType(), dto.getReportName(), dto.getCronExpression(),
                dto.getDeliveryChannels(), dto.getDeliveryEmails(), dto.getParams(),
                userId, LocalDateTime.now(), 1
        );

        log.info("[ReportSubscription] 创建订阅: id={}, reportType={}, cron={}", id, dto.getReportType(), dto.getCronExpression());
        return BaseResponse.ok(id);
    }

    /**
     * 查询当前用户的订阅列表
     *
     * @return 订阅列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询我的订阅列表")
    public BaseResponse<List<Map<String, Object>>> list() {
        String userId = AuthContext.getUserId();
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, report_type, report_name, cron_expression, delivery_channels, " +
                        "delivery_emails, params, status, last_run_at, last_run_status, " +
                        "created_at, updated_at " +
                        "FROM pmis_report_subscription " +
                        "WHERE created_by = ? AND deleted = 0 ORDER BY created_at DESC",
                userId
        );
        return BaseResponse.ok(list);
    }

    /**
     * 暂停/恢复订阅
     *
     * @param id     订阅 ID
     * @param status 目标状态（ACTIVE/PAUSED）
     */
    @Idempotent(key = "reportSub:toggle", ttlSeconds = 3, message = "请勿重复提交")
    @PutMapping("/{id}/status")
    @Operation(summary = "暂停/恢复订阅")
    public BaseResponse<Void> toggleStatus(@PathVariable String id, @RequestParam String status) {
        jdbcTemplate.update(
                "UPDATE pmis_report_subscription SET status = ?, updated_at = ? WHERE id = ?",
                status, LocalDateTime.now(), id
        );
        return BaseResponse.ok();
    }

    /**
     * 删除订阅（软删除）
     *
     * @param id 订阅 ID
     */
    @Idempotent(key = "reportSub:delete", ttlSeconds = 3, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除订阅")
    public BaseResponse<Void> delete(@PathVariable String id) {
        jdbcTemplate.update(
                "UPDATE pmis_report_subscription SET deleted = 1, updated_at = ? WHERE id = ?",
                LocalDateTime.now(), id
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
    public BaseResponse<List<Map<String, Object>>> history(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT id, subscription_id, run_at, run_status, file_url, error_message, " +
                        "duration_ms, file_size " +
                        "FROM pmis_report_subscription_log " +
                        "WHERE subscription_id = ? ORDER BY run_at DESC LIMIT ? OFFSET ?",
                id, size, offset
        );
        return BaseResponse.ok(history);
    }

    /**
     * 报表订阅创建 DTO
     */
    @Data
    public static class ReportSubscriptionDTO {
        @NotBlank(message = "报表类型不能为空")
        private String reportType;

        @NotBlank(message = "报表名称不能为空")
        private String reportName;

        @NotBlank(message = "Cron 表达式不能为空")
        private String cronExpression;

        @NotBlank(message = "投递渠道不能为空")
        private String deliveryChannels;

        private String deliveryEmails;

        @NotNull(message = "报表参数不能为空")
        private String params;
    }
}
