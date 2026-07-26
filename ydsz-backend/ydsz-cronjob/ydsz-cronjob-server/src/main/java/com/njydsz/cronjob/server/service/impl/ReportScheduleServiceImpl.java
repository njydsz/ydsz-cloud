package com.njydsz.cronjob.server.service.impl.job;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.MessageServiceClient;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.cronjob.server.service.job.ReportScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报表定时任务服务实现。
 *
 * <p>P1-9: 文件上传委托 {@link IFileStorageProvider}（common-file 公共抽象），
 * 不再直接依赖 MinioClient，可无缝切换 Local/MinIO/S3/OSS/COS 等存储后端。
 *
 * <ul>
 *   <li>{@link #generateReport} 按 reportType 生成 Excel，上传至公共文件存储，返回对象 key</li>
 *   <li>{@link #distributeReport} 落库 ydsz_export_record（source='SUBSCRIPTION'，P0-3 合并），
 *       并通过 Feign 调用 message 模块发送 EMAIL 通知</li>
 * </ul>
 *
 * <p>P0-3: 原 ydsz_report_export_record 已并入 ydsz_export_record，
 * 通过 source='SUBSCRIPTION' + subscription_id 区分订阅触发的导出记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportScheduleServiceImpl implements ReportScheduleService {

    /** 报表对象前缀（与原 MinIO report/ 路径对齐，由 IFileStorage 统一管理） */
    private static final String REPORT_PREFIX = "report/";

    /** Excel MIME Type */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** JDBC 模板（报表数据查询） */
    private final JdbcTemplate jdbcTemplate;
    /** P1-9: 公共文件存储提供者（替代直接依赖 MinioClient） */
    private final IFileStorageProvider fileStorageProvider;
    /** P1-8: 报表分发邮件通知（Feign 调用 message 模块） */
    private final MessageServiceClient messageServiceClient;

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
        String sql = "SELECT * FROM ydsz_report_subscription "
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
     * 生成报表：按 reportType 构建 Excel → 上传至公共文件存储 → 返回对象 key。
     *
     * <p>异常由 {@link #executeDailyReports} 捕获并跳过该订阅。
     *
     * @param reportType 报表类型（COCKPIT/EVM/PROFIT/UTILIZATION…）
     * @param params     订阅参数（含订阅元数据）
     * @return 文件存储对象 key
     */
    @Override
    public String generateReport(String reportType, Map<String, Object> params) {
        log.info("[ReportSchedule] 生成报表: type={}", reportType);
        ReportData data = buildReportData(reportType, params);
        byte[] bytes = writeExcel(reportType, data);
        String fileKey;
        try {
            fileKey = uploadToFileStorage(reportType, bytes);
        } catch (Exception e) {
            throw new IllegalStateException("[ReportSchedule] 文件存储上传报表失败: type=" + reportType, e);
        }
        log.info("[ReportSchedule] 报表生成完成: type={}, fileKey={}, size={}", reportType, fileKey, bytes.length);
        return fileKey;
    }

    /**
     * 分发报表：落库 ydsz_export_record（source='SUBSCRIPTION'，P0-3 合并），
     * 并通过 Feign 调用 message 模块发送 EMAIL 通知。
     *
     * <p>邮件发送失败仅记录日志，不影响记录落库与调度主流程；
     * 此时状态置 COMPLETED（文件已生成）+ 错误信息回写，邮件侧独立由 message 模块重试。
     *
     * @param subId      订阅 ID
     * @param reportType 报表类型
     * @param fileKey    文件存储对象 key
     * @param recipients 接收人（逗号分隔邮箱）
     * @param channels   分发通道
     */
    @Override
    public void distributeReport(Long subId, String reportType, String fileKey, String recipients, String channels) {
        // 1. 落库 ydsz_export_record，source='SUBSCRIPTION' 标记订阅触发
        //    字段顺序与 ydsz_export_record 完全对齐（避免 SQL 字段错位）
        String sql = "INSERT INTO ydsz_export_record ("
                + "tenant_id, source, user_id, export_type, report_type, subscription_id, "
                + "file_key, file_url, file_size, status, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // export_type 与 report_type 同时填：export_type='SUBSCRIPTION_REPORT' 作为统一类型
        // report_type 保留订阅侧语义（COCKPIT/EVM/PROFIT...）
        Long subscriberId = resolveSubscriberId(subId);
        jdbcTemplate.update(sql,
                "1",
                "SUBSCRIPTION",
                subscriberId,
                "SUBSCRIPTION_REPORT",
                reportType,
                subId,
                fileKey,
                fileKey,
                null,
                "COMPLETED",
                LocalDateTime.now());
        log.info("[ReportSchedule] 报表记录落库: subId={}, type={}, fileKey={}", subId, reportType, fileKey);

        // 2. 通过 Feign 调用 message 模块发送 EMAIL 通知
        sendEmailNotification(reportType, fileKey, recipients, channels, subId);
    }

    /**
     * 根据订阅 ID 解析订阅人 ID。
     *
     * <p>用于回填 ydsz_export_record.user_id，使前端下载中心可以按 user_id 筛选
     * 「我订阅的报表」。若订阅不存在或异常，返回 null（数据库允许 user_id 为空，
     * 因为 source='SUBSCRIPTION' 时 user_id 仅为辅助查询字段）。
     *
     * @param subId 订阅 ID
     * @return 订阅人 ID，失败时返回 null
     */
    private Long resolveSubscriberId(Long subId) {
        if (subId == null) {
            return null;
        }
        try {
            Long subscriberId = jdbcTemplate.queryForObject(
                    "SELECT subscriber_id FROM ydsz_report_subscription WHERE id = ?",
                    Long.class, subId);
            return subscriberId;
        } catch (Exception e) {
            log.warn("[ReportSchedule] 解析订阅人失败: subId={}, error={}", subId, e.getMessage());
            return null;
        }
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
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ExcelFacade.write(baos)
                .head(data.headers)
                .headRowNumber(0)
                .sheet(reportType == null ? "报表" : reportType)
                .doWrite(data.rows);
        return baos.toByteArray();
    }

    // ============================== 文件存储上传（P1-9: 委托 IFileStorage） ==============================

    /**
     * 上传报表到公共文件存储（P1-9: 委托 {@link IFileStorage}）。
     *
     * <p>存储后端由 {@code ydsz.file.type} 配置决定（local/minio/s3/oss/cos/qiniu/obs），
     * 业务层不感知具体实现。返回 {@link FileStorage#getUuidName()} 作为对象 key，
     * 供后续 ydsz_export_record.file_key 落库使用。
     *
     * @param reportType 报表类型（用于构建对象 key 前缀）
     * @param bytes      Excel 字节流
     * @return 文件存储对象 key
     * @throws Exception 上传失败时抛出（由调用方捕获）
     */
    private String uploadToFileStorage(String reportType, byte[] bytes) throws Exception {
        String objectName = REPORT_PREFIX + (reportType == null ? "REPORT" : reportType) + "/"
                + System.currentTimeMillis() + ".xlsx";
        IFileStorage storage = fileStorageProvider.getStorage();
        MultipartFile multipartFile = new InMemoryMultipartFile(
                "file", objectName, XLSX_CONTENT_TYPE, bytes);
        FileStorage result = storage.upload(null, objectName, multipartFile);
        // uuidName 为存储端实际对象 key（经 resolveObjectKey 规范化处理）
        return result.getUuidName();
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
            MessageRequest request = new MessageRequest();
            request.setChannel("EMAIL");
            request.setReceiver(recipients);
            request.setSubject("【YDSZ报表】" + (reportType == null ? "" : reportType) + " 报表已生成");
            request.setContent("您好，您订阅的 " + reportType + " 报表已生成，下载链接：" + fileKey);
            request.setBizType("REPORT");
            request.setBizId(String.valueOf(subId));
            BaseResponse<MessageResult> result = messageServiceClient.send(request);
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

    /**
     * 内存 MultipartFile 实现（P1-9: 用于将 Excel byte[] 传入 IFileStorage.upload）。
     *
     * <p>报表生成场景下，数据已在内存中（EasyExcel 写入 ByteArrayOutputStream），
     * 无需落盘。本类提供 MultipartFile 接口的最小内存实现，避免依赖 spring-test 的 MockMultipartFile。
     *
     * <p>参考 nextwiki-server 的 InMemoryMultipartFile 实现，保持一致性。
     */
    private static class InMemoryMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename,
                               String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            try (OutputStream os = Files.newOutputStream(dest.toPath())) {
                os.write(content);
            }
        }
    }
}
