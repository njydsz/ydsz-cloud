package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.config.MinioConfig;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.service.CockpitReportService;
import com.njydsz.pmis.execution.service.ReportService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AsyncExportServiceImpl 单元测试。
 *
 * <p>P1-8: 验证 executeExport 真正生成 Excel 并上传 MinIO，
 * 成功回写 COMPLETED，异常回写 FAILED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AsyncExportServiceImpl 异步导出服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncExportServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private ReportService reportService;
    @Mock
    private CockpitReportService cockpitReportService;

    @InjectMocks
    private AsyncExportServiceImpl service;

    @Test
    void submitExport_shouldInsertRecordAndReturnId() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong(), anyString()))
                .thenReturn(1L);
        Long id = service.submitExport(1L, "PROJECT", Map.of("status", "ACTIVE"));
        assertThat(id).isEqualTo(1L);
        verify(jdbcTemplate).update(anyString(), anyLong(), anyString(), anyString(), eq("PENDING"), any(), any());
    }

    @Test
    void getExportRecords_shouldReturnEmptyWhenNoRecords() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenReturn(0L);
        var result = service.getExportRecords(1L, PageRequest.of(0, 10));
        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getExportRecords_shouldReturnRecords() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(Map.of("id", 1, "status", "COMPLETED")));
        var result = service.getExportRecords(1L, PageRequest.of(0, 10));
        assertThat(result).hasSize(1);
    }

    @Test
    void getDownloadUrl_shouldReturnUrlWhenCompleted() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyLong()))
                .thenReturn("/download/file.xlsx");
        String url = service.getDownloadUrl(1L);
        assertThat(url).isEqualTo("/download/file.xlsx");
    }

    @Test
    void getDownloadUrl_shouldReturnNullWhenNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyLong()))
                .thenThrow(new RuntimeException("not found"));
        String url = service.getDownloadUrl(999L);
        assertThat(url).isNull();
    }

    @Test
    void deleteExportRecord_shouldUpdateDeletedFlag() {
        service.deleteExportRecord(1L);
        verify(jdbcTemplate).update(contains("SET deleted"), eq(1L));
    }

    @Test
    @DisplayName("executeExport 成功时应生成 Excel、上传 MinIO 并回写 COMPLETED")
    void executeExport_shouldGenerateExcelUploadMinioAndMarkCompleted() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");
        when(jdbcTemplate.queryForMap(anyString(), eq(1L)))
                .thenReturn(Map.of("export_type", "PROFIT", "params",
                        "{\"initiationId\":10,\"period\":\"2024-01\"}"));
        when(reportService.projectProfitReport(eq(10L), eq("2024-01")))
                .thenReturn(Map.of("revenue", new BigDecimal("1000"), "cost", new BigDecimal("400")));

        service.executeExport(1L);

        // 验证上传到 MinIO
        verify(minioClient, atLeastOnce()).putObject(any(PutObjectArgs.class));
        // 验证回写 COMPLETED（含 file_url/file_size）
        verify(jdbcTemplate).update(contains("COMPLETED"), anyString(), anyLong(), any(), eq(1L));
    }

    @Test
    @DisplayName("executeExport COCKPIT 类型应调用 cockpitReportService.overview")
    void executeExport_shouldCallCockpitServiceForCockpitType() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");
        when(jdbcTemplate.queryForMap(anyString(), eq(2L)))
                .thenReturn(Map.of("export_type", "COCKPIT", "params", "{}"));
        CockpitKpiVO kpi = new CockpitKpiVO();
        kpi.setActiveProjects(5);
        when(cockpitReportService.overview(isNull(), isNull())).thenReturn(kpi);

        service.executeExport(2L);

        verify(cockpitReportService).overview(isNull(), isNull());
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(jdbcTemplate).update(contains("COMPLETED"), anyString(), anyLong(), any(), eq(2L));
    }

    @Test
    @DisplayName("executeExport 数据为空时仍应上传空 Excel 并回写 COMPLETED")
    void executeExport_shouldHandleNullReportData() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");
        when(jdbcTemplate.queryForMap(anyString(), eq(3L)))
                .thenReturn(Map.of("export_type", "PROJECT", "params", "{}"));
        when(reportService.projectLifecycleReport(isNull())).thenReturn(null);

        service.executeExport(3L);

        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(jdbcTemplate).update(contains("COMPLETED"), anyString(), anyLong(), any(), eq(3L));
    }

    @Test
    @DisplayName("executeExport MinIO 上传失败时应回写 FAILED")
    void executeExport_shouldMarkFailedWhenMinioUploadFails() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");
        when(jdbcTemplate.queryForMap(anyString(), eq(4L)))
                .thenReturn(Map.of("export_type", "PROJECT", "params", "{}"));
        when(reportService.projectLifecycleReport(isNull()))
                .thenReturn(Map.of("k", "v"));
        // 模拟 MinIO 上传抛出受检异常
        org.mockito.Mockito.doThrow(new RuntimeException("minio down"))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        service.executeExport(4L);

        verify(jdbcTemplate).update(contains("FAILED"), anyString(), any(), eq(4L));
    }

    @Test
    @DisplayName("executeExport 查询记录失败时应回写 FAILED")
    void executeExport_shouldMarkFailedWhenRecordQueryFails() {
        when(jdbcTemplate.queryForMap(anyString(), anyLong()))
                .thenThrow(new RuntimeException("DB error"));
        service.executeExport(5L);
        verify(jdbcTemplate).update(contains("FAILED"), anyString(), any(), eq(5L));
    }
}
