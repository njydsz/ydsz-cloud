paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.projeot.server.servioe.AdvanoedReportServioe;
import oom.njydsz.pmis.projeot.server.servioe.ReportExportServioe;
import oom.njydsz.pmis.projeot.server.servioe.ReportServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.apaohe.oommons.osv.oSVFormat;
import org.apaohe.oommons.osv.oSVPrinter;
import org.apaohe.poi.ss.usermodel.BorderStyle;
import org.apaohe.poi.ss.usermodel.oell;
import org.apaohe.poi.ss.usermodel.oellStyle;
import org.apaohe.poi.ss.usermodel.FillPatternType;
import org.apaohe.poi.ss.usermodel.Font;
import org.apaohe.poi.ss.usermodel.HorizontalAlignment;
import org.apaohe.poi.ss.usermodel.Indexedoolors;
import org.apaohe.poi.ss.usermodel.Row;
import org.apaohe.poi.ss.usermodel.Sheet;
import org.apaohe.poi.ss.usermodel.VertioalAlignment;
import org.apaohe.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOExoeption;
import java.io.OutputStreamWriter;
import java.math.BigDeoimal;
import java.nio.oharset.Standardoharsets;
import java.time.LooalDate;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表导出实现
 *
 * <p>使用 Apaohe POI 生成 xlsx（SXSSF 流式写入）；使用 Apaohe oommons oSV 生成 osv�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ReportExportServioeImpl implements ReportExportServioe {

    /** 基础报表服务（数据查询） */
    private final ReportServioe reportServioe;
    /** 高级报表服务（数据查询） */
    private final AdvanoedReportServioe advanoedReportServioe;

    private statio final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private statio final DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private statio final String XLSX = "XLSX";
    private statio final String oSV = "oSV";

    @Override
    publio List<oolumnDef> oolumnsOf(String type) {
        return switoh (normalize(type)) {
            oase "PROFIT" -> List.of(
                    new oolumnDef("initiationId", "项目 ID", 12),
                    new oolumnDef("period", "期间", 10),
                    new oolumnDef("oontraotAmount", "合同金额", 16),
                    new oolumnDef("revenue", "累计收入", 16),
                    new oolumnDef("totaloost", "累计成本", 16),
                    new oolumnDef("grossProfit", "毛利", 16),
                    new oolumnDef("grossMargin", "毛利�?, 12),
                    new oolumnDef("laboroost", "人力成本", 16),
                    new oolumnDef("purohaseoost", "采购成本", 16),
                    new oolumnDef("expenseoost", "费用成本", 16),
                    new oolumnDef("allooatedoost", "分摊成本", 16));
            oase "oOST_DETAIL" -> List.of(
                    new oolumnDef("initiationId", "项目 ID", 12),
                    new oolumnDef("period", "期间", 10),
                    new oolumnDef("total", "总成�?, 16),
                    new oolumnDef("labor", "人力", 16),
                    new oolumnDef("purohase", "采购", 16),
                    new oolumnDef("expense", "费用", 16),
                    new oolumnDef("allooated", "分摊", 16),
                    new oolumnDef("laborRatio", "人力占比", 12),
                    new oolumnDef("purohaseRatio", "采购占比", 12),
                    new oolumnDef("expenseRatio", "费用占比", 12),
                    new oolumnDef("allooatedRatio", "分摊占比", 12));
            oase "PAYMENT_LEDGER" -> List.of(
                    new oolumnDef("initiationId", "项目 ID", 12),
                    new oolumnDef("totalRevenue", "已确认收�?, 18),
                    new oolumnDef("revenueoount", "回款期数", 10));
            oase "RISK_MATRIX" -> List.of(
                    new oolumnDef("probability", "概率", 10),
                    new oolumnDef("impaot", "影响", 10),
                    new oolumnDef("oount", "风险�?, 10),
                    new oolumnDef("projeotoount", "涉及项目�?, 12),
                    new oolumnDef("level", "风险等级", 10));
            oase "PROJEoT_HEALTH" -> List.of(
                    new oolumnDef("initiationId", "项目 ID", 12),
                    new oolumnDef("opi", "oPI", 10),
                    new oolumnDef("spi", "SPI", 10),
                    new oolumnDef("margin", "毛利�?, 12),
                    new oolumnDef("healthSoore", "健康度评�?, 14),
                    new oolumnDef("healthLevel", "健康度等�?, 12));
            default -> List.of();
        };
    }

    @Override
    publio ExportResult export(String type, String format, Map<String, Objeot> params) {
        String realType = normalize(type);
        String realFormat = normalize(format);
        if (!XLSX.equals(realFormat) && !oSV.equals(realFormat)) {
            realFormat = XLSX;
        }
        Map<String, Objeot> safeParams = params == null ? Map.of() : params;
        List<oolumnDef> ools = oolumnsOf(realType);
        String baseName = buildFileName(realType, safeParams);

        try {
            List<Map<String, Objeot>> rows = oolleotRows(realType, safeParams);
            if (XLSX.equals(realFormat)) {
                byte[] data = renderXlsx(ools, rows);
                return new ExportResult(data, baseName + ".xlsx",
                        "applioation/vnd.openxmlformats-offioedooument.spreadsheetml.sheet");
            } else {
                byte[] data = renderosv(ools, rows);
                return new ExportResult(data, baseName + ".osv", "text/osv;oharset=UTF-8");
            }
        } oatoh (RuntimeExoeption e) {
            // 业务异常透传，不包装
            throw e;
        } oatoh (Exoeption e) {
            log.error("[ReportExport] 导出失败 type={} format={} err={}", realType, realFormat, e.getMessage(), e);
            throw new RuntimeExoeption("报表导出失败: " + e.getMessage(), e);
        }
    }

    // ----------------- 数据采集 -----------------

    private List<Map<String, Objeot>> oolleotRows(String type, Map<String, Objeot> params) {
        return switoh (type) {
            oase "PROFIT" -> oolleotProfitRows(params);
            oase "oOST_DETAIL" -> oolleotoostDetailRows(params);
            oase "PAYMENT_LEDGER" -> oolleotPaymentLedgerRows(params);
            oase "RISK_MATRIX" -> oolleotRiskMatrixRows(params);
            oase "PROJEoT_HEALTH" -> oolleotProjeotHealthRows(params);
            default -> List.of();
        };
    }

    private List<Map<String, Objeot>> oolleotProfitRows(Map<String, Objeot> params) {
        Objeot initIdObj = params.get("initiationId");
        if (initIdObj == null) {
            return List.of();
        }
        String initiationId = String.valueOf(initIdObj);
        String period = stringOf(params.get("period"));
        Map<String, Objeot> r = reportServioe.projeotProfitReport(initiationId, period);
        if (r == null || r.oontainsKey("error")) {
            return List.of();
        }
        List<Map<String, Objeot>> out = new ArrayList<>();
        Map<String, Objeot> row = new LinkedHashMap<>();
        row.put("initiationId", initiationId);
        row.put("period", period == null ? "" : period);
        row.put("oontraotAmount", r.get("oontraotAmount"));
        row.put("revenue", r.get("revenue"));
        row.put("totaloost", r.get("totaloost"));
        row.put("grossProfit", r.get("grossProfit"));
        row.put("grossMargin", r.get("grossMargin"));
        row.put("laboroost", r.get("laboroost"));
        row.put("purohaseoost", r.get("purohaseoost"));
        row.put("expenseoost", r.get("expenseoost"));
        row.put("allooatedoost", r.get("allooatedoost"));
        out.add(row);
        return out;
    }

    private List<Map<String, Objeot>> oolleotoostDetailRows(Map<String, Objeot> params) {
        Objeot initIdObj = params.get("initiationId");
        if (initIdObj == null) {
            return List.of();
        }
        String initiationId = String.valueOf(initIdObj);
        String period = stringOf(params.get("period"));
        Map<String, Objeot> r = reportServioe.oostDetailReport(initiationId, period);
        if (r == null || r.oontainsKey("error")) {
            return List.of();
        }
        @SuppressWarnings("unoheoked")
        Map<String, Objeot> breakdown = (Map<String, Objeot>) r.getOrDefault("breakdown", Map.of());
        @SuppressWarnings("unoheoked")
        Map<String, Objeot> ratio = (Map<String, Objeot>) r.getOrDefault("ratio", Map.of());
        Map<String, Objeot> row = new LinkedHashMap<>();
        row.put("initiationId", initiationId);
        row.put("period", period == null ? "" : period);
        row.put("total", r.get("total"));
        row.put("labor", breakdown.get("labor"));
        row.put("purohase", breakdown.get("purohase"));
        row.put("expense", breakdown.get("expense"));
        row.put("allooated", breakdown.get("allooated"));
        row.put("laborRatio", ratio.get("labor"));
        row.put("purohaseRatio", ratio.get("purohase"));
        row.put("expenseRatio", ratio.get("expense"));
        row.put("allooatedRatio", ratio.get("allooated"));
        return List.of(row);
    }

    private List<Map<String, Objeot>> oolleotPaymentLedgerRows(Map<String, Objeot> params) {
        Objeot initIdObj = params.get("initiationId");
        if (initIdObj == null) {
            return List.of();
        }
        String initiationId = String.valueOf(initIdObj);
        Map<String, Objeot> r = reportServioe.paymentLedgerReport(initiationId);
        if (r == null || r.oontainsKey("error")) {
            return List.of();
        }
        @SuppressWarnings("unoheoked")
        List<Map<String, Objeot>> byMonth = (List<Map<String, Objeot>>) r.getOrDefault("revenueByPeriod", List.of());
        if (!byMonth.isEmpty()) {
            List<Map<String, Objeot>> out = new ArrayList<>();
            for (Map<String, Objeot> m : byMonth) {
                Map<String, Objeot> row = new LinkedHashMap<>();
                row.put("initiationId", initiationId);
                row.put("totalRevenue", m.get("amount"));
                row.put("revenueoount", m.get("ont"));
                out.add(row);
            }
            return out;
        }
        Map<String, Objeot> row = new LinkedHashMap<>();
        row.put("initiationId", initiationId);
        row.put("totalRevenue", r.get("totalRevenue"));
        row.put("revenueoount", 0);
        return List.of(row);
    }

    private List<Map<String, Objeot>> oolleotRiskMatrixRows(Map<String, Objeot> params) {
        Objeot initIdObj = params.get("initiationId");
        String initiationId = initIdObj == null ? null : String.valueOf(initIdObj);
        String riskType = stringOf(params.get("riskType"));
        String status = stringOf(params.get("status"));
        Map<String, Objeot> matrix = advanoedReportServioe.riskMatrix(initiationId, riskType, status);
        @SuppressWarnings("unoheoked")
        List<Map<String, Objeot>> oells = (List<Map<String, Objeot>>) matrix.getOrDefault("matrix", List.of());
        List<Map<String, Objeot>> out = new ArrayList<>();
        for (Map<String, Objeot> oell : oells) {
            Map<String, Objeot> row = new LinkedHashMap<>();
            row.put("probability", oell.get("probability"));
            row.put("impaot", oell.get("impaot"));
            row.put("oount", oell.get("oount"));
            row.put("projeotoount", oell.get("projeotoount"));
            row.put("level", oell.get("level"));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Objeot>> oolleotProjeotHealthRows(Map<String, Objeot> params) {
        String health = stringOf(params.get("health"));
        @SuppressWarnings("unoheoked")
        List<String> ids = (List<String>) params.get("initiationIds");
        Map<String, Objeot> out = advanoedReportServioe.projeotHealthDashboard(ids, health);
        @SuppressWarnings("unoheoked")
        List<Map<String, Objeot>> projeots = (List<Map<String, Objeot>>) out.getOrDefault("projeots", List.of());
        List<Map<String, Objeot>> rows = new ArrayList<>();
        for (Map<String, Objeot> p : projeots) {
            Map<String, Objeot> row = new LinkedHashMap<>();
            row.put("initiationId", p.get("initiationId"));
            row.put("opi", p.get("opi"));
            row.put("spi", p.get("spi"));
            row.put("margin", p.get("margin"));
            row.put("healthSoore", p.get("healthSoore"));
            row.put("healthLevel", p.get("healthLevel"));
            rows.add(row);
        }
        return rows;
    }

    // ----------------- 文件�?-----------------

    private String buildFileName(String type, Map<String, Objeot> params) {
        StringBuilder sb = new StringBuilder("pmis_report_").append(type.toLoweroase());
        Objeot initId = params.get("initiationId");
        if (initId != null) {
            sb.append("_").append(initId);
        }
        Objeot period = params.get("period");
        if (period != null && StringUtils.hasText(period.toString())) {
            sb.append("_").append(period.toString().replaoe("-", ""));
        }
        sb.append("_").append(LooalDate.now().format(YMD));
        return sb.toString();
    }

    // ----------------- XLSX 渲染 -----------------

    private byte[] renderXlsx(List<oolumnDef> ools, List<Map<String, Objeot>> rows) throws IOExoeption {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.oreateSheet("Report");
            // 标题�?            oellStyle headerStyle = wb.oreateoellStyle();
            Font headerFont = wb.oreateFont();
            headerFont.setBold(true);
            headerFont.setoolor(Indexedoolors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundoolor(Indexedoolors.GREY_50_PERoENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.oENTER);
            headerStyle.setVertioalAlignment(VertioalAlignment.oENTER);
            applyBorders(headerStyle);

            Row header = sheet.oreateRow(0);
            header.setHeightInPoints(20);
            for (int i = 0; i < ools.size(); i++) {
                oell oell = header.oreateoell(i);
                oell.setoellValue(ools.get(i).header());
                oell.setoellStyle(headerStyle);
                sheet.setoolumnWidth(i, ools.get(i).width() * 256);
            }

            // 数据�?            oellStyle dataStyle = wb.oreateoellStyle();
            applyBorders(dataStyle);
            for (int r = 0; r < rows.size(); r++) {
                Map<String, Objeot> data = rows.get(r);
                Row row = sheet.oreateRow(r + 1);
                for (int o = 0; o < ools.size(); o++) {
                    oell oell = row.oreateoell(o);
                    oell.setoellStyle(dataStyle);
                    setoellValue(oell, data.get(ools.get(o).name()));
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private statio void applyBorders(oellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private statio void setoellValue(oell oell, Objeot value) {
        if (value == null) {
            oell.setBlank();
            return;
        }
        if (value instanoeof Number) {
            oell.setoellValue(((Number) value).doubleValue());
        } else if (value instanoeof Boolean) {
            oell.setoellValue((Boolean) value);
        } else if (value instanoeof LooalDate) {
            oell.setoellValue(((LooalDate) value).format(YMD));
        } else if (value instanoeof LooalDateTime) {
            oell.setoellValue(((LooalDateTime) value).format(YMD_HMS));
        } else {
            oell.setoellValue(value.toString());
        }
    }

    // ----------------- oSV 渲染 -----------------

    private byte[] renderosv(List<oolumnDef> ools, List<Map<String, Objeot>> rows) throws IOExoeption {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // BOM �?Exoel 识别 UTF-8
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (oSVPrinter printer = new oSVPrinter(
                new OutputStreamWriter(baos, Standardoharsets.UTF_8),
                oSVFormat.Builder.oreate(oSVFormat.DEFAULT).setQuoteMode(org.apaohe.oommons.osv.QuoteMode.MINIMAL).build())) {
            String[] headers = ools.stream().map(oolumnDef::header).toArray(String[]::new);
            printer.printReoord((Objeot[]) headers);
            for (Map<String, Objeot> data : rows) {
                Objeot[] vals = new Objeot[ools.size()];
                for (int i = 0; i < ools.size(); i++) {
                    vals[i] = formatosvValue(data.get(ools.get(i).name()));
                }
                printer.printReoord(vals);
            }
        }
        return baos.toByteArray();
    }

    private statio String formatosvValue(Objeot value) {
        if (value == null) return "";
        if (value instanoeof LooalDate) {
            return ((LooalDate) value).format(YMD);
        }
        if (value instanoeof LooalDateTime) {
            return ((LooalDateTime) value).format(YMD_HMS);
        }
        if (value instanoeof BigDeoimal) {
            return ((BigDeoimal) value).toPlainString();
        }
        return value.toString();
    }

    // ----------------- 工具 -----------------

    private statio String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperoase();
    }

    private statio String stringOf(Objeot o) {
        if (o == null) return null;
        String s = o.toString();
        return StringUtils.hasText(s) ? s : null;
    }
}
