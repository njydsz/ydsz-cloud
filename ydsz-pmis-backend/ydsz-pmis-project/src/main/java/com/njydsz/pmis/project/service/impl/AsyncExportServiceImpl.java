package com.njydsz.pmis.project.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.excel.EasyExcel;
import com.njydsz.pmis.project.config.MinioConfig;
import com.njydsz.pmis.project.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.project.service.AsyncExportService;
import com.njydsz.pmis.project.service.CockpitReportService;
import com.njydsz.pmis.project.service.ReportService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异步导出服务实现。
 *
 * <p>处理流程：
 * <ol>
 *   <li>提交导出任务 → PENDING 状态入库</li>
 *   <li>定时 Job 拉取 PENDING 任务 → GENERATING → 生成 Excel → 上传 MinIO → COMPLETED</li>
 *   <li>前端轮询或通过 WebSocket 通知完成</li>
 * </ol>
 *
 * <p>P1-8: {@link #executeExport(Long)} 根据 exportType 调用对应报表 Service 查询数据，
 * 使用 EasyExcel 生成 XLSX，上传至 MinIO，并回写 file_url/file_size/status。
 * 任意环节异常 → 状态置 FAILED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncExportServiceImpl implements AsyncExportService {

    /** 错误信息入库最大长度 */
    private static final int ERROR_MSG_MAX_LEN = 500;
    /** MinIO 导出对象前缀 */
    private static final String EXPORT_PREFIX = "export/";

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final ReportService reportService;
    private final CockpitReportService cockpitReportService;

    @Override
    public Long submitExport(Long userId, String exportType, Map<String, Object> params) {
        String sql = "INSERT INTO pmis_export_record (user_id, export_type, params, status, created_at, expired_at) "
                + "VALUES (?, ?, ?::text, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, userId, exportType, toJson(params), "PENDING", now, now.plusDays(7));
        Long id = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM pmis_export_record WHERE user_id = ? AND export_type = ?",
                Long.class, userId, exportType);
        log.info("[AsyncExport] 提交导出任务: id={}, userId={}, type={}", id, userId, exportType);
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getExportRecords(Long userId, Pageable pageable) {
        String countSql = "SELECT COUNT(*) FROM pmis_export_record WHERE user_id = ? AND deleted = 0";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, userId);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String sql = "SELECT * FROM pmis_export_record WHERE user_id = ? AND deleted = 0 "
                + "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                sql, userId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(Long recordId) {
        String sql = "SELECT file_url FROM pmis_export_record WHERE id = ? AND deleted = 0 AND status = 'COMPLETED'";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, recordId);
        } catch (Exception e) {
            log.warn("[AsyncExport] 获取下载URL失败: recordId={}, error={}", recordId, e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteExportRecord(Long recordId) {
        jdbcTemplate.update("UPDATE pmis_export_record SET deleted = 1 WHERE id = ?", recordId);
        log.info("[AsyncExport] 删除导出记录: id={}", recordId);
    }

    /**
     * 执行导出：生成 Excel → 上传 MinIO → 回写记录状态。
     *
     * <p>异常时状态置 FAILED，不影响调度主流程。
     *
     * @param recordId 导出记录 ID
     */
    @Override
    public void executeExport(Long recordId) {
        try {
            jdbcTemplate.update("UPDATE pmis_export_record SET status = 'GENERATING' WHERE id = ?", recordId);
            Map<String, Object> record = jdbcTemplate.queryForMap(
                    "SELECT * FROM pmis_export_record WHERE id = ?", recordId);
            String exportType = (String) record.get("export_type");
            Map<String, Object> params = parseParams(record.get("params"));
            log.info("[AsyncExport] 开始生成导出文件: id={}, type={}", recordId, exportType);

            // 1. 根据 exportType 查询数据
            ReportData data = fetchReportData(exportType, params);
            // 2. 生成 Excel
            byte[] bytes = writeExcel(exportType, data);
            // 3. 上传到 MinIO
            String fileUrl = uploadToMinio(recordId, exportType, bytes);
            // 4. 回写记录
            jdbcTemplate.update(
                    "UPDATE pmis_export_record SET status = 'COMPLETED', file_url = ?, file_size = ?, completed_at = ? WHERE id = ?",
                    fileUrl, (long) bytes.length, LocalDateTime.now(), recordId);
            log.info("[AsyncExport] 导出完成: id={}, fileUrl={}, size={}", recordId, fileUrl, bytes.length);
        } catch (Exception e) {
            log.error("[AsyncExport] 导出失败: id={}, error={}", recordId, e.getMessage());
            jdbcTemplate.update(
                    "UPDATE pmis_export_record SET status = 'FAILED', error_message = ?, completed_at = ? WHERE id = ?",
                    truncate(e.getMessage()), LocalDateTime.now(), recordId);
        }
    }

    // ============================== 报表数据查询 ==============================

    /**
     * 根据导出类型调用对应报表 Service 查询数据。
     *
     * @param exportType 导出类型
     * @param params     查询参数（initiationId / period / deptId）
     * @return 表头与数据行
     */
    private ReportData fetchReportData(String exportType, Map<String, Object> params) {
        Long initiationId = getLong(params, "initiationId");
        String period = getString(params, "period");
        Long deptId = getLong(params, "deptId");
        String type = exportType == null ? "" : exportType;
        switch (type) {
            case "COCKPIT":
                CockpitDrillDownDTO drillDown = null;
                if (deptId != null) {
                    drillDown = new CockpitDrillDownDTO();
                    drillDown.setDimension("DEPT");
                    drillDown.setValue(String.valueOf(deptId));
                }
                return toReportData("驾驶舱KPI", cockpitReportService.overview(period, drillDown));
            case "PROFIT":
                return toReportData("利润数据", reportService.projectProfitReport(initiationId, period));
            case "PAYMENT":
                return toReportData("回款台账", reportService.paymentLedgerReport(initiationId));
            case "COST":
                return toReportData("成本明细", reportService.costDetailReport(initiationId, period));
            case "LIFECYCLE":
            case "PROJECT":
            default:
                return toReportData("立项信息", reportService.projectLifecycleReport(initiationId));
        }
    }

    /**
     * 将任意报表数据对象转为表头 + 数据行。
     *
     * <p>支持 Map / POJO（通过 fastjson2 转 Map）/ null。
     *
     * @param title 报表标题（仅用于日志）
     * @param data  报表数据
     * @return 表头与数据行
     */
    @SuppressWarnings("unchecked")
    private ReportData toReportData(String title, Object data) {
        if (data == null) {
            log.warn("[AsyncExport] {} 报表数据为空", title);
            return new ReportData(List.of(), List.of());
        }
        Map<String, Object> map;
        if (data instanceof Map) {
            map = new LinkedHashMap<>((Map<String, Object>) data);
        } else {
            // POJO → Map（保留字段顺序）
            map = JSON.parseObject(JSON.toJSONString(data));
        }
        if (map.isEmpty()) {
            return new ReportData(List.of(), List.of());
        }
        List<String> headers = new ArrayList<>(map.keySet());
        List<Object> row = new ArrayList<>(map.values());
        return new ReportData(headers, List.of(row));
    }

    // ============================== Excel 生成 ==============================

    /**
     * 使用 EasyExcel 生成 XLSX 字节流。
     *
     * @param exportType 导出类型（sheet 名）
     * @param data       表头与数据行
     * @return XLSX 字节流
     */
    private byte[] writeExcel(String exportType, ReportData data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<List<String>> head = data.headers.stream()
                .map(Collections::singletonList)
                .collect(Collectors.toList());
        EasyExcel.write(baos)
                .head(head)
                .sheet(exportType == null ? "导出数据" : exportType)
                .doWrite(data.rows);
        return baos.toByteArray();
    }

    // ============================== MinIO 上传 ==============================

    /**
     * 上传 Excel 字节流到 MinIO。
     *
     * @param recordId   导出记录 ID
     * @param exportType 导出类型
     * @param bytes      Excel 字节流
     * @return MinIO 对象 key
     * @throws Exception 上传失败时抛出（由外层 try-catch 捕获置 FAILED）
     */
    private String uploadToMinio(Long recordId, String exportType, byte[] bytes) throws Exception {
        String objectKey = EXPORT_PREFIX + recordId + "/"
                + (exportType == null ? "EXPORT" : exportType) + "_"
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

    // ============================== 工具方法 ==============================

    /**
     * 解析 params 字段为 Map。
     *
     * @param raw params 原始值（JSON 字符串）
     * @return 参数 Map，空时返回空 Map
     */
    private Map<String, Object> parseParams(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        String json = raw.toString();
        if (json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(json);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("[AsyncExport] params 解析失败，按空参数处理: {}", json);
            return Map.of();
        }
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > ERROR_MSG_MAX_LEN ? msg.substring(0, ERROR_MSG_MAX_LEN) : msg;
    }

    private String toJson(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        return JSON.toJSONString(params);
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
