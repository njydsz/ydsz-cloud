package com.njydsz.pmis.cronjob.service.impl;

import com.alibaba.excel.EasyExcel;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageFeignClient;
import com.njydsz.pmis.cronjob.config.MinioConfig;
import com.njydsz.pmis.cronjob.service.ReportScheduleService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表定时任务服务实现。
 *
 * <p>P1-8:
 * <ul>
 *   <li>{@link #generateReport} 按 reportType 生成 Excel，上传 MinIO，返回对象 key</li>
 *   <li>{@link #distributeReport} 落库 pmis_report_export_record，并通过 Feign 调用 message 模块发送 EMAIL 通知</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportScheduleServiceImpl implements ReportScheduleService {

    /** MinIO 报表对象前缀 */
    private static final String REPORT_PREFIX = "report/";

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    /** P1-8: 报表分发邮件通知（Feign 调用 message 模块） */
    private final MessageFeignClient messageFeignClient;

    @Override
    public void executeDailyReports() {
        executeReportsByFrequency("DAILY");
    }

    @Override
    public void executeWeeklyReports() {
        executeReportsByFrequency("WEEKLY");
    }

    @Override
    public void executeMonthlyReports() {
        executeReportsByFrequency("MONTHLY");
    }

    /**
     * 按 frequency 字段拉取订阅并逐条生成/分发报表。
     *
     * <p>无对应频率订阅时静默返回；单条异常不影响其他订阅。
     *
     * @param frequency 订阅频率（DAILY / WEEKLY / MONTHLY）
     */
    private void executeReportsByFrequency(String frequency) {
        String sql = "SELECT * FROM pmis_report_subscription "
                + "WHERE status = 1 AND deleted = 0 AND frequency = ?";
        List<Map<String, Object>> subs = jdbcTemplate.queryForList(sql, frequency);
        if (subs.isEmpty()) {
            log.info("[ReportSchedule] 无 {} 订阅，跳过", frequency);
            return;
        }
        log.info("[ReportSchedule] 开始处理 {} 订阅: count={}", frequency, subs.size());
        for (Map<String, Object> sub : subs) {
            try {
                Long subId = ((Number) sub.get("id")).longValue();
                String reportType = (String) sub.get("report_type");
                String recipients = (String) sub.get("recipients");
                String channels = (String) sub.get("channels");
                log.info("[ReportSchedule] 处理订阅: subId={}, type={}, recipients={}",
                        subId, reportType, recipients);
                String fileKey = generateReport(reportType, sub);
                distributeReport(subId, reportType, fileKey, recipients, channels);
            } catch (Exception e) {
                log.error("[ReportSchedule] 订阅处理失败: sub={}, error={}", sub.get("id"), e.getMessage(), e);
            }
        }
    }

    /**
     * 生成报表：按 reportType 构建 Excel → 上传 MinIO → 返回对象 key。
     *
     * <p>异常由 {@link #executeDailyReports} 捕获并跳过该订阅。
     *
     * @param reportType 报表类型（COCKPIT/EVM/PROFIT/UTILIZATION…）
     * @param params     订阅参数（含订阅元数据）
     * @return MinIO 对象 key
     */
    @Override
    public String generateReport(String reportType, Map<String, Object> params) {
        log.info("[ReportSchedule] 生成报表: type={}", reportType);
        ReportData data = buildReportData(reportType, params);
        byte[] bytes = writeExcel(reportType, data);
        String fileKey;
        try {
            fileKey = uploadToMinio(reportType, bytes);
        } catch (Exception e) {
            throw new IllegalStateException("[ReportSchedule] MinIO 上传报表失败: type=" + reportType, e);
        }
        log.info("[ReportSchedule] 报表生成完成: type={}, fileKey={}, size={}", reportType, fileKey, bytes.length);
        return fileKey;
    }

    /**
     * 分发报表：落库 pmis_report_export_record，并通过 Feign 调用 message 模块发送 EMAIL 通知。
     *
     * <p>邮件发送失败仅记录日志，不影响记录落库与调度主流程。
     *
     * @param subId      订阅 ID
     * @param reportType 报表类型
     * @param fileKey    MinIO 对象 key
     * @param recipients 接收人（逗号分隔邮箱）
     * @param channels   分发通道
     */
    @Override
    public void distributeReport(Long subId, String reportType, String fileKey, String recipients, String channels) {
        // 1. 落库 pmis_report_export_record
        String sql = "INSERT INTO pmis_report_export_record "
                + "(subscription_id, report_type, file_url, status, generated_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, subId, reportType, fileKey, "COMPLETED", LocalDateTime.now());
        log.info("[ReportSchedule] 报表记录落库: subId={}, type={}, fileKey={}", subId, reportType, fileKey);

        // 2. 通过 Feign 调用 message 模块发送 EMAIL 通知
        sendEmailNotification(reportType, fileKey, recipients, channels, subId);
    }

    // ============================== 报表数据构建 ==============================

    /**
     * 根据报表类型构建表头与数据行。
     *
     * <p>不同 reportType 产出不同结构；公共元数据（报表类型 / 生成时间）统一前置。
     *
     * @param reportType 报表类型
     * @param params     订阅参数
     * @return 表头与数据行
     */
    private ReportData buildReportData(String reportType, Map<String, Object> params) {
        String type = reportType == null ? "" : reportType;
        List<String> headers;
        List<List<Object>> rows = new ArrayList<>();
        switch (type) {
            case "COCKPIT":
                headers = List.of("指标", "数值");
                rows.add(List.of("活跃项目数", getParam(params, "activeProjects")));
                rows.add(List.of("合同总额", getParam(params, "totalContractAmount")));
                rows.add(List.of("确认收入", getParam(params, "confirmedRevenue")));
                rows.add(List.of("总成本", getParam(params, "totalCost")));
                break;
            case "EVM":
                headers = List.of("项目", "CPI", "SPI", "状态");
                rows.add(List.of(getParam(params, "projectName"), getParam(params, "cpi"),
                        getParam(params, "spi"), getParam(params, "status")));
                break;
            case "PROFIT":
                headers = List.of("项目", "收入", "成本", "利润", "利润率");
                rows.add(List.of(getParam(params, "projectName"), getParam(params, "revenue"),
                        getParam(params, "cost"), getParam(params, "profit"), getParam(params, "margin")));
                break;
            case "UTILIZATION":
                headers = List.of("部门", "可计费工时", "总工时", "可计费利用率");
                rows.add(List.of(getParam(params, "department"), getParam(params, "billableHours"),
                        getParam(params, "totalHours"), getParam(params, "utilizationRate")));
                break;
            default:
                // 通用：按订阅参数键值输出
                headers = List.of("字段", "数值");
                if (params != null) {
                    params.forEach((k, v) -> rows.add(List.of(k, v == null ? "" : v)));
                }
                break;
        }
        // 前置公共元数据行
        List<List<Object>> withMeta = new ArrayList<>();
        withMeta.add(List.of("报表类型", type));
        withMeta.add(List.of("生成时间", LocalDateTime.now().toString()));
        withMeta.addAll(rows);
        return new ReportData(headers, withMeta);
    }

    private Object getParam(Map<String, Object> params, String key) {
        return params == null ? "" : params.getOrDefault(key, "");
    }

    // ============================== Excel 生成 ==============================

    /**
     * 使用 EasyExcel 生成 XLSX 字节流。
     */
    private byte[] writeExcel(String reportType, ReportData data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<List<String>> head = data.headers.stream()
                .map(Collections::singletonList)
                .collect(Collectors.toList());
        EasyExcel.write(baos)
                .head(head)
                .sheet(reportType == null ? "报表" : reportType)
                .doWrite(data.rows);
        return baos.toByteArray();
    }

    // ============================== MinIO 上传 ==============================

    /**
     * 上传报表到 MinIO。
     *
     * @throws Exception 上传失败时抛出（由调用方捕获）
     */
    private String uploadToMinio(String reportType, byte[] bytes) throws Exception {
        String objectKey = REPORT_PREFIX + (reportType == null ? "REPORT" : reportType) + "/"
                + System.currentTimeMillis() + ".xlsx";
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getDefaultBucket())
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build());
        }
        return objectKey;
    }

    // ============================== 邮件通知 ==============================

    /**
     * 通过 Feign 调用 message 模块发送 EMAIL 报表通知。
     *
     * <p>非关键路径，失败仅记录日志。
     */
    private void sendEmailNotification(String reportType, String fileKey, String recipients,
                                       String channels, Long subId) {
        if (recipients == null || recipients.isBlank()) {
            log.warn("[ReportSchedule] 无接收人，跳过邮件通知: subId={}", subId);
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("channel", "EMAIL");
            payload.put("receiver", recipients);
            payload.put("subject", "【PMIS报表】" + (reportType == null ? "" : reportType) + " 报表已生成");
            payload.put("content", "您好，您订阅的 " + reportType + " 报表已生成，下载链接：" + fileKey);
            payload.put("bizType", "REPORT");
            payload.put("bizId", String.valueOf(subId));
            Result<Map<String, Object>> result = messageFeignClient.send(payload);
            if (result != null && result.isSuccess()) {
                log.info("[ReportSchedule] 报表邮件通知发送成功: subId={}, recipients={}", subId, recipients);
            } else {
                log.warn("[ReportSchedule] 报表邮件通知发送失败: subId={}, result={}", subId, result);
            }
        } catch (Exception e) {
            log.warn("[ReportSchedule] 报表邮件通知异常: subId={}, recipients={}, error={}",
                    subId, recipients, e.getMessage());
        }
    }

    /**
     * 报表数据持有者（表头 + 数据行）。
     */
    private static class ReportData {
        final List<String> headers;
        final List<List<Object>> rows;

        ReportData(List<String> headers, List<List<Object>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }
}
