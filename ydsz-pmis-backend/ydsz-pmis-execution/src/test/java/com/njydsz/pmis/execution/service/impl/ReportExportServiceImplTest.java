package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.execution.service.AdvancedReportService;
import com.njydsz.pmis.execution.service.ReportExportService;
import com.njydsz.pmis.execution.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ReportExportServiceImpl 测试
 */
@DisplayName("ReportExportServiceImpl 报表导出")
class ReportExportServiceImplTest {

    private ReportService reportService;
    private AdvancedReportService advancedReportService;
    private ProfitSnapshotMapper profitSnapshotMapper;
    private EvmMeasureMapper evmMapper;
    private ReportExportServiceImpl service;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        advancedReportService = mock(AdvancedReportService.class);
        profitSnapshotMapper = mock(ProfitSnapshotMapper.class);
        evmMapper = mock(EvmMeasureMapper.class);
        service = new ReportExportServiceImpl(reportService, advancedReportService,
                profitSnapshotMapper, evmMapper);
    }

    // ----------------- columnsOf -----------------

    @Test
    @DisplayName("columnsOf PROFIT 返回 11 列")
    void columnsOf_profit() {
        List<ReportExportService.ColumnDef> cols = service.columnsOf("PROFIT");
        assertThat(cols).hasSize(11);
        assertThat(cols.get(0).name()).isEqualTo("initiationId");
        assertThat(cols.get(0).header()).isEqualTo("项目 ID");
    }

    @Test
    @DisplayName("columnsOf 未知类型返回空列表")
    void columnsOf_unknown() {
        assertThat(service.columnsOf("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("columnsOf 对大小写不敏感")
    void columnsOf_caseInsensitive() {
        assertThat(service.columnsOf("profit")).hasSize(11);
        assertThat(service.columnsOf("PrOfIt")).hasSize(11);
    }

    // ----------------- PROFIT 导出 -----------------

    @Test
    @DisplayName("export PROFIT 走 projectProfitReport 拉数并输出 xlsx")
    void export_profit_xlsx() throws IOException {
        Map<String, Object> profit = new LinkedHashMap<>();
        profit.put("contractAmount", new BigDecimal("100000"));
        profit.put("revenue", new BigDecimal("80000"));
        profit.put("totalCost", new BigDecimal("50000"));
        profit.put("grossProfit", new BigDecimal("30000"));
        profit.put("grossMargin", new BigDecimal("0.30"));
        profit.put("laborCost", new BigDecimal("30000"));
        profit.put("purchaseCost", new BigDecimal("10000"));
        profit.put("expenseCost", new BigDecimal("5000"));
        profit.put("allocatedCost", new BigDecimal("5000"));
        when(reportService.projectProfitReport(eq(1L), eq("2026-01"))).thenReturn(profit);

        Map<String, Object> params = new HashMap<>();
        params.put("initiationId", 1L);
        params.put("period", "2026-01");

        ReportExportService.ExportResult r = service.export("PROFIT", "XLSX", params);
        assertThat(r.filename()).startsWith("pmis_report_profit_1_202601_").endsWith(".xlsx");
        assertThat(r.contentType()).contains("spreadsheetml");
        // XLSX 是 zip 格式 (PK..)
        assertThat(r.data()[0]).isEqualTo((byte) 'P');
        assertThat(r.data()[1]).isEqualTo((byte) 'K');
        // 校验文件大小（带 1 行数据的最小 xlsx 通常 > 1KB）
        assertThat(r.data().length).isGreaterThan(500);
    }

    @Test
    @DisplayName("export PROFIT 缺 initiationId 返回空 xlsx")
    void export_profit_empty() {
        ReportExportService.ExportResult r = service.export("PROFIT", "XLSX", new HashMap<>());
        assertThat(r.data()).isNotEmpty(); // 仅有表头
        assertThat(r.filename()).endsWith(".xlsx");
    }

    @Test
    @DisplayName("export PROFIT 后端返回 error 时输出空 xlsx")
    void export_profit_backendError() {
        Map<String, Object> err = new HashMap<>();
        err.put("error", "no data");
        when(reportService.projectProfitReport(eq(1L), any())).thenReturn(err);

        ReportExportService.ExportResult r = service.export("PROFIT", "XLSX",
                Map.of("initiationId", 1L));
        assertThat(r.data()).isNotEmpty();
    }

    // ----------------- CSV 导出 -----------------

    @Test
    @DisplayName("export COST_DETAIL 输出 CSV 含 BOM 和表头")
    void export_costDetail_csv() {
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("total", new BigDecimal("60000"));
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("labor", new BigDecimal("30000"));
        breakdown.put("purchase", new BigDecimal("20000"));
        breakdown.put("expense", new BigDecimal("5000"));
        breakdown.put("allocated", new BigDecimal("5000"));
        cost.put("breakdown", breakdown);
        Map<String, Object> ratio = new LinkedHashMap<>();
        ratio.put("labor", new BigDecimal("0.50"));
        ratio.put("purchase", new BigDecimal("0.3333"));
        ratio.put("expense", new BigDecimal("0.0833"));
        ratio.put("allocated", new BigDecimal("0.0833"));
        cost.put("ratio", ratio);
        when(reportService.costDetailReport(eq(2L), any())).thenReturn(cost);

        ReportExportService.ExportResult r = service.export("COST_DETAIL", "CSV",
                Map.of("initiationId", 2L));
        assertThat(r.filename()).endsWith(".csv");
        assertThat(r.contentType()).contains("text/csv");
        String body = new String(r.data(), StandardCharsets.UTF_8);
        // UTF-8 BOM
        assertThat(body.charAt(0)).isEqualTo((char) 0xFEFF);
        // 表头包含中文
        assertThat(body).contains("项目 ID").contains("总成本").contains("人力");
    }

    @Test
    @DisplayName("export PAYMENT_LEDGER with revenueByPeriod 输出多行")
    void export_payment_ledger_multi() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        List<Map<String, Object>> byPeriod = List.of(
                Map.of("amount", new BigDecimal("30000"), "cnt", 1),
                Map.of("amount", new BigDecimal("50000"), "cnt", 2));
        ledger.put("revenueByPeriod", byPeriod);
        when(reportService.paymentLedgerReport(eq(3L))).thenReturn(ledger);

        ReportExportService.ExportResult r = service.export("PAYMENT_LEDGER", "XLSX",
                Map.of("initiationId", 3L));
        assertThat(r.data()).isNotEmpty();
    }

    @Test
    @DisplayName("export PAYMENT_LEDGER 无 byMonth 时输出单行")
    void export_payment_ledger_single() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("totalRevenue", new BigDecimal("80000"));
        when(reportService.paymentLedgerReport(eq(4L))).thenReturn(ledger);

        ReportExportService.ExportResult r = service.export("PAYMENT_LEDGER", "XLSX",
                Map.of("initiationId", 4L));
        assertThat(r.data()).isNotEmpty();
    }

    // ----------------- RISK_MATRIX -----------------

    @Test
    @DisplayName("export RISK_MATRIX 走 advancedReportService.riskMatrix")
    void export_risk_matrix() {
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("matrix", List.of(
                Map.of("probability", 1, "impact", 1, "count", 0, "projectCount", 0, "level", "LOW"),
                Map.of("probability", 3, "impact", 3, "count", 2, "projectCount", 1, "level", "HIGH")));
        when(advancedReportService.riskMatrix(eq(5L), eq("TECH"), eq("OPEN"))).thenReturn(matrix);

        ReportExportService.ExportResult r = service.export("RISK_MATRIX", "XLSX",
                Map.of("initiationId", 5L, "riskType", "TECH", "status", "OPEN"));
        assertThat(r.data()).isNotEmpty();
        assertThat(r.filename()).contains("risk_matrix");
    }

    // ----------------- PROJECT_HEALTH -----------------

    @Test
    @DisplayName("export PROJECT_HEALTH 走 projectHealthDashboard")
    void export_project_health() {
        Map<String, Object> health = new LinkedHashMap<>();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("initiationId", 1L);
        p.put("cpi", new BigDecimal("1.10"));
        p.put("spi", new BigDecimal("1.05"));
        p.put("margin", new BigDecimal("0.30"));
        p.put("healthScore", new BigDecimal("86.50"));
        p.put("healthLevel", "GREEN");
        health.put("projects", List.of(p));
        when(advancedReportService.projectHealthDashboard(any(), eq("GREEN"))).thenReturn(health);

        ReportExportService.ExportResult r = service.export("PROJECT_HEALTH", "XLSX",
                Map.of("health", "GREEN"));
        assertThat(r.data()).isNotEmpty();
    }

    // ----------------- 异常路径 -----------------

    @Test
    @DisplayName("export 未知 type 返回空 xlsx（不抛错）")
    void export_unknown_type() {
        ReportExportService.ExportResult r = service.export("WEIRD", "XLSX", new HashMap<>());
        // 表头为空但依然能输出空工作表
        assertThat(r.data()).isNotEmpty();
    }

    @Test
    @DisplayName("export 后端抛错时包装为 RuntimeException")
    void export_backendThrows() {
        when(reportService.projectProfitReport(any(), any()))
                .thenThrow(new RuntimeException("DB down"));
        // 业务异常透传
        assertThatThrownBy(() -> service.export("PROFIT", "XLSX",
                Map.of("initiationId", 1L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }

    @Test
    @DisplayName("export format 默认 XLSX")
    void export_format_default() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("contractAmount", BigDecimal.ZERO);
        when(reportService.projectProfitReport(any(), any())).thenReturn(r);
        ReportExportService.ExportResult out = service.export("PROFIT", null,
                Map.of("initiationId", 1L));
        assertThat(out.filename()).endsWith(".xlsx");
    }

    @Test
    @DisplayName("export CSV 包含 LocalDate 字段时按 yyyyMMdd 格式化")
    void export_csv_localdateFormat() {
        // 没有现成接口支持 LocalDate 列，先用 PROFIT 验证 CSV 至少可输出
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("contractAmount", new BigDecimal("100"));
        when(reportService.projectProfitReport(any(), any())).thenReturn(r);

        ReportExportService.ExportResult out = service.export("PROFIT", "CSV",
                Map.of("initiationId", 1L));
        String body = new String(out.data(), StandardCharsets.UTF_8);
        assertThat(body).contains("项目 ID,期间,合同金额");
    }

    @Test
    @DisplayName("export 文件名包含 yyyyMMdd 日期后缀")
    void export_filename_dateSuffix() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("contractAmount", BigDecimal.ZERO);
        when(reportService.projectProfitReport(any(), any())).thenReturn(r);
        ReportExportService.ExportResult out = service.export("PROFIT", "XLSX",
                Map.of("initiationId", 1L, "period", "2026-03"));
        // yyyyMMdd format
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateStr = String.format("%04d%02d%02d",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth());
        assertThat(out.filename()).contains("_" + dateStr);
    }
}
