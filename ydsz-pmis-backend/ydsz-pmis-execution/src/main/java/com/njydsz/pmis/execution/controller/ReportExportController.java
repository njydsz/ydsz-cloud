package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表导出 Controller
 *
 * <p>支持将基础/高级报表导出为 xlsx 或 csv（P2-6 体验增强）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "报表导出")
@RestController
@RequestMapping("/api/v1/execution/report-export")
@RequiredArgsConstructor
public class ReportExportController {

    private final ReportExportService exportService;

    @Operation(summary = "查询可导出的列定义")
    @GetMapping("/columns")
    public Result<List<ReportExportService.ColumnDef>> columns(@RequestParam String type) {
        return Result.ok(exportService.columnsOf(type));
    }

    @Operation(summary = "下载报表（XLSX/CSV，P2-6）")
    @GetMapping("/download")
    public void download(
            @RequestParam String type,
            @RequestParam(defaultValue = "XLSX") String format,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String health,
            HttpServletResponse response) throws IOException {
        // 1) 收集通用参数
        Map<String, Object> params = new LinkedHashMap<>();
        if (initiationId != null) {
            params.put("initiationId", initiationId);
        }
        if (period != null && !period.isBlank()) {
            params.put("period", period);
        }
        if (department != null && !department.isBlank()) {
            params.put("department", department);
        }
        if (riskType != null && !riskType.isBlank()) {
            params.put("riskType", riskType);
        }
        if (status != null && !status.isBlank()) {
            params.put("status", status);
        }
        if (health != null && !health.isBlank()) {
            params.put("health", health);
        }

        // 2) 调用导出
        ReportExportService.ExportResult result = exportService.export(type, format, params);

        // 3) 写入 HTTP 响应
        String filename = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(result.contentType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(result.data().length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(result.data());
            out.flush();
        }
        log.info("[ReportExport] type={} format={} size={} bytes", type, format, result.data().length);
    }
}
