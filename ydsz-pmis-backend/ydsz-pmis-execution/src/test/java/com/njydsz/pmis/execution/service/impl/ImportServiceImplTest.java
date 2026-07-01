package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.excel.ExcelUtil;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.controller.ImportExportController;
import com.njydsz.pmis.execution.dto.RateCardCreateDTO;
import com.njydsz.pmis.execution.dto.RateCardImportDTO;
import com.njydsz.pmis.execution.service.ImportService;
import com.njydsz.pmis.execution.service.RateCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ImportServiceImpl 批量导入单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ImportServiceImpl 批量导入测试")
class ImportServiceImplTest {

    private RateCardService rateCardService;
    private ImportServiceImpl service;

    @BeforeEach
    void setUp() {
        rateCardService = mock(RateCardService.class);
        service = new ImportServiceImpl(rateCardService);
    }

    @Test
    @DisplayName("buildTemplate rate-card 返回非空 bytes + 正确 headClass")
    void buildTemplate_rateCard() {
        ImportExportController.TemplateBundle bundle = service.buildTemplate("rate-card");
        assertThat(bundle).isNotNull();
        assertThat(bundle.bytes()).isNotEmpty();
        assertThat(bundle.bytes().length).isGreaterThan(1000);
        assertThat(bundle.headClass()).isEqualTo(RateCardImportDTO.class);
        assertThat(bundle.filename()).endsWith(".xlsx");
    }

    @Test
    @DisplayName("buildTemplate 不支持的 bizType 抛 BizException")
    void buildTemplate_unsupported() {
        assertThatThrownBy(() -> service.buildTemplate("not-exist"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂不支持");
    }

    @Test
    @DisplayName("importFile rate-card 真正的空 xlsx 返回 0/0/0")
    void importFile_empty() throws Exception {
        // 准备一个真正空的 xlsx（仅表头、无数据行）
        byte[] empty = buildRateCardXlsx(Collections.emptyList());
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(empty));

        ImportService.ImportResult result = service.importFile("rate-card", file);
        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    @DisplayName("importFile 不支持的 bizType 抛 BizException")
    void importFile_unsupported() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.xlsx", "application/octet-stream", new byte[]{});
        assertThatThrownBy(() -> service.importFile("not-exist", file))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("importFile rate-card 校验失败行被记录到 failures")
    void importFile_rateCard_withFailures() throws Exception {
        // 准备一个带 2 行数据的 xlsx
        byte[] xlsx = buildRateCardXlsx(List.of(
                row("L5", "ENT", "T&M", "1800.00", "2026-07-01", "2026-12-31", "CNY", "正常"),
                row("", "GOV", "FIXED_PRICE", "2000.00", "2026-08-01", "", "CNY", "level 空将失败")
        ));

        // 第一行调用 service.create 返回 1L，第二行抛 BizException
        when(rateCardService.create(any())).thenReturn(1L);
        org.mockito.Mockito.doThrow(new BizException(400, "职级不能为空"))
                .when(rateCardService).create(org.mockito.ArgumentMatchers.argThat(dto -> dto.getLevelCode() == null || dto.getLevelCode().isBlank()));

        MockMultipartFile file = new MockMultipartFile(
                "file", "rate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(xlsx));

        ImportService.ImportResult result = service.importFile("rate-card", file);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).rowIndex()).isEqualTo(3);  // header(1) + data row 1(2) = 失败行在第 3 行
        assertThat(result.failures().get(0).reason()).contains("职级");
    }

    @Test
    @DisplayName("importFile rate-card 全部成功 failures 为空")
    void importFile_rateCard_allSuccess() throws Exception {
        byte[] xlsx = buildRateCardXlsx(List.of(
                row("L5", "ENT", "T&M", "1800.00", "2026-07-01", "", "CNY", "成功1"),
                row("L6", "GOV", "MILESTONE", "2500.00", "2026-07-01", "2026-12-31", "CNY", "成功2")
        ));
        when(rateCardService.create(any())).thenReturn(1L);

        MockMultipartFile file = new MockMultipartFile(
                "file", "rate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(xlsx));

        ImportService.ImportResult result = service.importFile("rate-card", file);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(0);
        assertThat(result.failures()).isEmpty();

        ArgumentCaptor<RateCardCreateDTO> captor = ArgumentCaptor.forClass(RateCardCreateDTO.class);
        verify(rateCardService, times(2)).create(captor.capture());
        assertThat(captor.getAllValues().get(0).getLevelCode()).isEqualTo("L5");
        assertThat(captor.getAllValues().get(1).getLevelCode()).isEqualTo("L6");
    }

    // ==================== helper ====================

    private static String[] row(String level, String customer, String project,
                                String amount, String effDate, String expDate,
                                String currency, String remark) {
        return new String[]{level, customer, project, amount, effDate, expDate, currency, remark};
    }

    /**
     * 通过 ExcelUtil 写出 xlsx 再读回（保持与生产一致）
     */
    private static byte[] buildRateCardXlsx(List<String[]> rows) throws Exception {
        List<RateCardImportDTO> data = new ArrayList<>();
        for (String[] r : rows) {
            RateCardImportDTO dto = new RateCardImportDTO();
            dto.setLevel(r[0]);
            dto.setCustomerType(r[1]);
            dto.setProjectType(r[2]);
            dto.setUnitPrice(r[3] == null || r[3].isEmpty() ? null : new BigDecimal(r[3]));
            dto.setEffectiveDate(r[4]);
            dto.setExpiryDate(r[5]);
            dto.setCurrency(r[6]);
            dto.setRemark(r[7]);
            data.add(dto);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.exportMultiSheet(
                out, RateCardImportDTO.class,
                List.of(new ExcelUtil.ExcelSheet<>("费率卡", data)));
        return out.toByteArray();
    }
}
