package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.project.server.service.AdvancedReportService;
import com.njydsz.pmis.project.server.service.ReportExportService;
import com.njydsz.pmis.project.server.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.QuoteMode;

/**
 * 报表导出实现
 *
 * <p>使用 Apache POI 生成 xlsx（SXSSF 流式写入）；使用 Apache Commons CSV 生成 csv。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    /** 基础报表服务（数据查询） */
    private final ReportService reportService;
    /** 高级报表服务（数据查询） */
    private final AdvancedReportService advancedReportService;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String XLSX = "XLSX";
    private static final String CSV = "CSV";

    @Override
    public List<ColumnDef> columnsOf(String type) {
        return switch (normalize(type)) {
            case "PROFIT" -> List.of(
                    new ColumnDef("initiationId", "项目 ID", 12),
                    new ColumnDef("period", "期间", 10),
                    new ColumnDef("contractAmount", "合同金额", 16),
                    new ColumnDef("revenue", "累计收入", 16),
                    new ColumnDef("totalCost", "累计成本", 16),
                    new ColumnDef("grossProfit", "毛利", 16),
                    new ColumnDef("grossMargin", "毛利率", 12),
                    new ColumnDef("laborCost", "人力成本", 16),
                    new ColumnDef("purchaseCost", "采购成本", 16),
                    new ColumnDef("expenseCost", "费用成本", 16),
                    new ColumnDef("allocatedCost", "分摊成本", 16));
            case "COST_DETAIL" -> List.of(
                    new ColumnDef("initiationId", "项目 ID", 12),
                    new ColumnDef("period", "期间", 10),
                    new ColumnDef("total", "总成本", 16),
                    new ColumnDef("labor", "人力", 16),
                    new ColumnDef("purchase", "采购", 16),
                    new ColumnDef("expense", "费用", 16),
                    new ColumnDef("allocated", "分摊", 16),
                    new ColumnDef("laborRatio", "人力占比", 12),
                    new ColumnDef("purchaseRatio", "采购占比", 12),
                    new ColumnDef("expenseRatio", "费用占比", 12),
                    new ColumnDef("allocatedRatio", "分摊占比", 12));
            case "PAYMENT_LEDGER" -> List.of(
                    new ColumnDef("initiationId", "项目 ID", 12),
                    new ColumnDef("totalRevenue", "已确认收入", 18),
                    new ColumnDef("revenueCount", "回款期数", 10));
            case "RISK_MATRIX" -> List.of(
                    new ColumnDef("probability", "概率", 10),
                    new ColumnDef("impact", "影响", 10),
                    new ColumnDef("count", "风险数", 10),
                    new ColumnDef("projectCount", "涉及项目数", 12),
                    new ColumnDef("level", "风险等级", 10));
            case "PROJECT_HEALTH" -> List.of(
                    new ColumnDef("initiationId", "项目 ID", 12),
                    new ColumnDef("cpi", "CPI", 10),
                    new ColumnDef("spi", "SPI", 10),
                    new ColumnDef("margin", "毛利率", 12),
                    new ColumnDef("healthScore", "健康度评分", 14),
                    new ColumnDef("healthLevel", "健康度等级", 12));
            default -> List.of();
        };
    }

    @Override
    public ExportResult export(String type, String format, Map<String, Object> params) {
        String realType = normalize(type);
        String realFormat = normalize(format);
        if (!XLSX.equals(realFormat) && !CSV.equals(realFormat)) {
            realFormat = XLSX;
        }
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        List<ColumnDef> cols = columnsOf(realType);
        String baseName = buildFileName(realType, safeParams);

        try {
            List<Map<String, Object>> rows = collectRows(realType, safeParams);
            if (XLSX.equals(realFormat)) {
                byte[] data = renderXlsx(cols, rows);
                return new ExportResult(data, baseName + ".xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            } else {
                byte[] data = renderCsv(cols, rows);
                return new ExportResult(data, baseName + ".csv", "text/csv;charset=UTF-8");
            }
        } catch (RuntimeException e) {
            // 业务异常透传，不包装
            throw e;
        } catch (Exception e) {
            log.error("[ReportExport] 导出失败 type={} format={} err={}", realType, realFormat, e.getMessage(), e);
            throw new RuntimeException("报表导出失败: " + e.getMessage(), e);
        }
    }

    // ----------------- 数据采集 -----------------

    private List<Map<String, Object>> collectRows(String type, Map<String, Object> params) {
        return switch (type) {
            case "PROFIT" -> collectProfitRows(params);
            case "COST_DETAIL" -> collectCostDetailRows(params);
            case "PAYMENT_LEDGER" -> collectPaymentLedgerRows(params);
            case "RISK_MATRIX" -> collectRiskMatrixRows(params);
            case "PROJECT_HEALTH" -> collectProjectHealthRows(params);
            default -> List.of();
        };
    }

    private List<Map<String, Object>> collectProfitRows(Map<String, Object> params) {
        Object initIdObj = params.get("initiationId");
        if (initIdObj == null) {
            return List.of();
        }
        String initiationId = String.valueOf(initIdObj);
        String period = stringOf(params.get("period"));
        Map<String, Object> r = reportService.projectProfitReport(initiationId, period);
        if (r == null || r.containsKey("error")) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("initiationId", initiationId);
        row.put("period", period == null ? "" : period);
        row.put("contractAmount", r.get("contractAmount"));
        row.put("revenue", r.get("revenue"));
        row.put("totalCost", r.get("totalCost"));
        row.put("grossProfit", r.get("grossProfit"));
        row.put("grossMargin", r.get("grossMargin"));
        row.put("laborCost", r.get("laborCost"));
        row.put("purchaseCost", r.get("purchaseCost"));
        row.put("expenseCost", r.get("expenseCost"));
        row.put("allocatedCost", r.get("allocatedCost"));
        out.add(row);
        return out;
    }

    private List<Map<String, Object>> collectCostDetailRows(Map<String, Object> params) {
        Object initIdObj = params.get("initiationId");
        if (initIdObj == null) {
            return List.of();
        }
        String initiationId = String.valueOf(initIdObj);
        String period = stringOf(params.get("period"));
        Map<String, Object> r = reportService.costDetailReport(initiationId, period);
        if (r == null || r.containsKey("error")) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) r.getOrDefault("breakdown", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> ratio = (Map<String, Object>) r.getOrDefault("ratio", Map.of());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("initiationId", initiationId);
        row.put("period", period == null ? "" : period);
        row.put("total", r.get("total"));
        row.put("labor", breakdown.get("labor"));
        row.put("purchase", breakdown.get("purchase"));
        row.put("expense", breakdown.get("expense"));
        row.put("allocated", breakdown.get("allocated"));
        row.put("laborRatio", ratio.get("labor"));
        row.put("purchaseRatio", ratio.get("purchase"));
        row.put("expenseRatio", ratio.get("expense"));
        row.put("allocatedRatio", ratio.get("allocated"));
        return List.of(row);
    }

    private List<Map<String, Object>> collectPaymentLedgerRows(Map<String, Object> params) {
        Object initIdObj = params.get("initiationId");
        if (initIdObj == null) {
            return List.of();
        }
        String initiationId = String.valueOf(initIdObj);
        Map<String, Object> r = reportService.paymentLedgerReport(initiationId);
        if (r == null || r.containsKey("error")) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byMonth = (List<Map<String, Object>>) r.getOrDefault("revenueByPeriod", List.of());
        if (!byMonth.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> m : byMonth) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("initiationId", initiationId);
                row.put("totalRevenue", m.get("amount"));
                row.put("revenueCount", m.get("cnt"));
                out.add(row);
            }
            return out;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("initiationId", initiationId);
        row.put("totalRevenue", r.get("totalRevenue"));
        row.put("revenueCount", 0);
        return List.of(row);
    }

    private List<Map<String, Object>> collectRiskMatrixRows(Map<String, Object> params) {
        Object initIdObj = params.get("initiationId");
        String initiationId = initIdObj == null ? null : String.valueOf(initIdObj);
        String riskType = stringOf(params.get("riskType"));
        String status = stringOf(params.get("status"));
        Map<String, Object> matrix = advancedReportService.riskMatrix(initiationId, riskType, status);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cells = (List<Map<String, Object>>) matrix.getOrDefault("matrix", List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> cell : cells) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("probability", cell.get("probability"));
            row.put("impact", cell.get("impact"));
            row.put("count", cell.get("count"));
            row.put("projectCount", cell.get("projectCount"));
            row.put("level", cell.get("level"));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> collectProjectHealthRows(Map<String, Object> params) {
        String health = stringOf(params.get("health"));
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) params.get("initiationIds");
        Map<String, Object> out = advancedReportService.projectHealthDashboard(ids, health);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.getOrDefault("projects", List.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> p : projects) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("initiationId", p.get("initiationId"));
            row.put("cpi", p.get("cpi"));
            row.put("spi", p.get("spi"));
            row.put("margin", p.get("margin"));
            row.put("healthScore", p.get("healthScore"));
            row.put("healthLevel", p.get("healthLevel"));
            rows.add(row);
        }
        return rows;
    }

    // ----------------- 文件名 -----------------

    private String buildFileName(String type, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("pmis_report_").append(type.toLowerCase());
        Object initId = params.get("initiationId");
        if (initId != null) {
            sb.append("_").append(initId);
        }
        Object period = params.get("period");
        if (period != null && StringUtils.hasText(period.toString())) {
            sb.append("_").append(period.toString().replace("-", ""));
        }
        sb.append("_").append(LocalDate.now().format(YMD));
        return sb.toString();
    }

    // ----------------- XLSX 渲染 -----------------

    private byte[] renderXlsx(List<ColumnDef> cols, List<Map<String, Object>> rows) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Report");
            // 标题行
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(headerStyle);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(20);
            for (int i = 0; i < cols.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols.get(i).header());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, cols.get(i).width() * 256);
            }

            // 数据行
            CellStyle dataStyle = wb.createCellStyle();
            applyBorders(dataStyle);
            for (int r = 0; r < rows.size(); r++) {
                Map<String, Object> data = rows.get(r);
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < cols.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellStyle(dataStyle);
                    setCellValue(cell, data.get(cols.get(c).name()));
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private static void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof LocalDate) {
            cell.setCellValue(((LocalDate) value).format(YMD));
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(((LocalDateTime) value).format(YMD_HMS));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    // ----------------- CSV 渲染 -----------------

    private byte[] renderCsv(List<ColumnDef> cols, List<Map<String, Object>> rows) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // BOM 让 Excel 识别 UTF-8
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(baos, StandardCharsets.UTF_8),
                CSVFormat.Builder.create(CSVFormat.DEFAULT).setQuoteMode(QuoteMode.MINIMAL).build())) {
            String[] headers = cols.stream().map(ColumnDef::header).toArray(String[]::new);
            printer.printRecord((Object[]) headers);
            for (Map<String, Object> data : rows) {
                Object[] vals = new Object[cols.size()];
                for (int i = 0; i < cols.size(); i++) {
                    vals[i] = formatCsvValue(data.get(cols.get(i).name()));
                }
                printer.printRecord(vals);
            }
        }
        return baos.toByteArray();
    }

    private static String formatCsvValue(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(YMD);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(YMD_HMS);
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return value.toString();
    }

    // ----------------- 工具 -----------------

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase();
    }

    private static String stringOf(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return StringUtils.hasText(s) ? s : null;
    }
}
