package com.njydsz.system.server.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.repository.VariableRepository;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.system.server.service.rollback.VariableRollbackStrategy;
import com.njydsz.system.server.vo.ImportResultVO;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 配置 / 变量批量导入测试（26.11.06 性能优化）。
 *
 * <p><b>覆盖目标</b>：{@code ConfigExcelServiceImpl.importConfigs} 与
 * {@code VariableServiceImpl.importVariables} 的分批读取 + 批量预加载判重 + 落库分支。
 *
 * <p>与 {@link DictItemBatchImportTest} 同构：POI 生成标准 xlsx → ExcelFacade 读取 →
 * 分批回调 → 校验 → 落库。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class ConfigBatchImportTest {

  private ConfigRepository configRepository;
  private VariableRepository variableRepository;
  private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    configRepository = mock(ConfigRepository.class);
    variableRepository = mock(VariableRepository.class);
    org.springframework.cache.Cache cache = mock(org.springframework.cache.Cache.class);
    cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache(any())).thenReturn(cache);
    CacheKeyBuilder cacheKeyBuilder = mock(CacheKeyBuilder.class);
    when(cacheKeyBuilder.dictList(any())).thenReturn("cache:key");
  }

  @Test
  @DisplayName("ConfigExcelServiceImpl：分批导入全部落库")
  void shouldImportConfigsInBatches() {
    when(configRepository.loadGroupKeySet(any())).thenReturn(new HashSet<>());
    when(configRepository.insertBatch(any())).thenReturn(true);

    ConfigExcelServiceImpl service =
        new ConfigExcelServiceImpl(
            configRepository,
            cacheManager,
            mock(CacheKeyBuilder.class),
            mock(ExcelExportHelper.class));

    ImportResultVO result =
        service.importConfigs(new ByteArrayInputStream(buildConfigXlsx(5)));

    assertThat(result.getTotalCount()).isEqualTo(5);
    assertThat(result.getSuccessCount()).isEqualTo(5);
    assertThat(result.getFailCount()).isZero();
  }

  @Test
  @DisplayName("ConfigExcelServiceImpl：空文件短路")
  void shouldShortCircuitOnEmptyConfigFile() {
    ConfigExcelServiceImpl service =
        new ConfigExcelServiceImpl(
            configRepository,
            cacheManager,
            mock(CacheKeyBuilder.class),
            mock(ExcelExportHelper.class));

    ImportResultVO result =
        service.importConfigs(
            new ByteArrayInputStream(
                buildEmptyXlsx(new String[] {"配置分组", "配置键", "配置值", "状态"})));

    assertThat(result.getTotalCount()).isZero();
  }

  @Test
  @DisplayName("VariableServiceImpl：分批导入全部落库")
  void shouldImportVariablesInBatches() {
    when(variableRepository.loadKeySet(any())).thenReturn(new HashSet<>());
    when(variableRepository.insert(any(VariableDTO.class))).thenReturn(true);

    VariableServiceImpl service = newVariableService();

    ImportResultVO result =
        service.importVariables(new ByteArrayInputStream(buildVariableXlsx(5)));

    assertThat(result.getTotalCount()).isEqualTo(5);
    assertThat(result.getSuccessCount()).isEqualTo(5);
    assertThat(result.getFailCount()).isZero();
  }

  @Test
  @DisplayName("VariableServiceImpl：DB 已存在同键跳过")
  void shouldSkipExistingVariables() {
    when(variableRepository.loadKeySet(any())).thenReturn(new HashSet<>(List.of("VAR_0")));
    when(variableRepository.insert(any(VariableDTO.class))).thenReturn(true);

    VariableServiceImpl service = newVariableService();

    ImportResultVO result =
        service.importVariables(new ByteArrayInputStream(buildVariableXlsx(3)));

    assertThat(result.getTotalCount()).isEqualTo(3);
    assertThat(result.getSkipCount()).isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(2);
  }

  /**
   * 构造 VariableServiceImpl（9 个依赖，多数为 mock 占位）。
   *
   * @return service 实例
   */
  @SuppressWarnings("unchecked")
  private VariableServiceImpl newVariableService() {
    ObjectProvider<DomainEventPublisher> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return new VariableServiceImpl(
        variableRepository,
        mock(SystemMetrics.class),
        mock(EntityVersionService.class),
        cacheManager,
        mock(CacheKeyBuilder.class),
        provider,
        mock(ExcelExportHelper.class),
        mock(VariableRollbackStrategy.class),
        mock(ApplicationEventPublisher.class));
  }

  /**
   * 生成配置 xlsx（N 行）。
   *
   * @param rows 行数
   * @return xlsx 字节流
   */
  private byte[] buildConfigXlsx(int rows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("sheet1");
      String[] headers = {"配置分组", "配置键", "配置值", "状态"};
      org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      for (int i = 0; i < rows; i++) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
        row.createCell(0).setCellValue("GROUP_A");
        row.createCell(1).setCellValue("CFG_" + i);
        row.createCell(2).setCellValue("值" + i);
        row.createCell(3).setCellValue("ENABLED");
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 生成变量 xlsx（N 行）。
   *
   * @param rows 行数
   * @return xlsx 字节流
   */
  private byte[] buildVariableXlsx(int rows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("sheet1");
      String[] headers = {"变量键", "变量值", "变量分组", "状态"};
      org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      for (int i = 0; i < rows; i++) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
        row.createCell(0).setCellValue("VAR_" + i);
        row.createCell(1).setCellValue("值" + i);
        row.createCell(2).setCellValue("GROUP_A");
        row.createCell(3).setCellValue("ENABLED");
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 生成仅含表头的空 xlsx。
   *
   * @param headers 表头
   * @return xlsx 字节流
   */
  private byte[] buildEmptyXlsx(String[] headers) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("sheet1");
      org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
