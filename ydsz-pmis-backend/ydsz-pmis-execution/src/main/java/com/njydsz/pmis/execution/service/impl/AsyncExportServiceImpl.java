package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.service.AsyncExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异步导出服务实现。
 *
 * <p>处理流程：
 * <ol>
 *   <li>提交导出任务 → PENDING 状态入库</li>
 *   <li>定时 Job 拉取 PENDING 任务 → GENERATING → 生成文件 → COMPLETED</li>
 *   <li>前端轮询或通过 WebSocket 通知完成</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
public class AsyncExportServiceImpl implements AsyncExportService {

    private static final Logger log = LoggerFactory.getLogger(AsyncExportServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public AsyncExportServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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

    @Override
    public void executeExport(Long recordId) {
        try {
            jdbcTemplate.update("UPDATE pmis_export_record SET status = 'GENERATING' WHERE id = ?", recordId);
            Map<String, Object> record = jdbcTemplate.queryForMap(
                    "SELECT * FROM pmis_export_record WHERE id = ?", recordId);
            String exportType = (String) record.get("export_type");
            log.info("[AsyncExport] 开始生成导出文件: id={}, type={}", recordId, exportType);
            // TODO: 根据 exportType 调用对应的报表 Service 生成 Excel/PDF
            // 生成后上传到 MinIO，更新 file_url/file_size/status
            String fileUrl = "/api/v1/file/download/export_" + recordId + ".xlsx";
            jdbcTemplate.update("UPDATE pmis_export_record SET status = 'COMPLETED', file_url = ?, completed_at = ? WHERE id = ?",
                    fileUrl, LocalDateTime.now(), recordId);
            log.info("[AsyncExport] 导出完成: id={}, fileUrl={}", recordId, fileUrl);
        } catch (Exception e) {
            log.error("[AsyncExport] 导出失败: id={}, error={}", recordId, e.getMessage());
            jdbcTemplate.update("UPDATE pmis_export_record SET status = 'FAILED', error_message = ?, completed_at = ? WHERE id = ?",
                    e.getMessage(), LocalDateTime.now(), recordId);
        }
    }

    private String toJson(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
