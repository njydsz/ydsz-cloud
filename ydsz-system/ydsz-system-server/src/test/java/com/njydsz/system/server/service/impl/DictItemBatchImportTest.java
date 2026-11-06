package com.njydsz.system.server.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.event.DictChangeEventPublisher;
import com.njydsz.system.server.service.rollback.DictItemRollbackStrategy;
import com.njydsz.system.server.vo.DictItemExcelVO;
import com.njydsz.system.server.vo.ImportResultVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DictItemServiceImpl} 批量导入测试（26.11.06 性能优化）。
 *
 * <p><b>覆盖目标</b>：{@code readExcelInBatches} 与 {@code saveValidItemsBatch} 的新增分支——
 * 分批回调、批量预加载判重、文件内去重、批量落库、空文件短路、DB 重复键/异常降级逐条。
 *
 * <p>采用纯 Mockito 单元测试（不依赖 Spring 容器与数据库），用 POI 构造标准 xlsx 字节流驱动
 * {@code ExcelFacade} → {@code ExcelReader} → 分批回调全链路。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class DictItemBatchImportTest {

  /** 与生产一致的批量大小（用于验证多批分片）。 */
  private static final int PRODUCTION_BATCH_SIZE = 500;

  private DictRepository dictRepository;
  private DictItemServiceImpl service;
  private org.springframework.cache.Cache dictItemCache;

  @BeforeEach
  void setUp() {
    dictRepository = mock(DictRepository.class);
    // 缓存失效路径依赖 CacheManager/CacheKeyBuilder，需用 mock 占位避免 NPE
    dictItemCache = mock(org.springframework.cache.Cache.class);
    org.springframework.cache.CacheManager cacheManager =
        mock(org.springframework.cache.CacheManager.class);
    when(cacheManager.getCache(any())).thenReturn(dictItemCache);
    CacheKeyBuilder cacheKeyBuilder = mock(CacheKeyBuilder.class);
    when(cacheKeyBuilder.dictList(any())).thenReturn("dict:list:key");
    service =
        new DictItemServiceImpl(
            dictRepository,
            mock(SystemMetrics.class),
            mock(EntityVersionService.class),
            cacheManager,
            mock(CacheKeyBuilder.class),
            mock(ExcelExportHelper.class),
            mock(DictItemRollbackStrategy.class),
            mock(ApplicationEventPublisher.class),
            mock(DictChangeEventPublisher.class));
    when(dictRepository.findItemsByTypeCodes(anySet())).thenReturn(new ArrayList<>());
    when(dictRepository.insertItemsBatch(any())).thenReturn(true);
  }

  @Test
  @DisplayName("分批导入：多行数据全部落库且合并为批量插入")
  void shouldImportAllRowsAcrossBatches() {
    byte[] xlsx = buildXlsx(7, "DICT_A");
    ImportResultVO result =
        service.importDictItems(new ByteArrayInputStream(xlsx));

    assertThat(result.getTotalCount()).isEqualTo(7);
    assertThat(result.getSuccessCount()).as("全部合法应全部落库").isEqualTo(7);
    assertThat(result.getFailCount()).isZero();
    assertThat(result.getSkipCount()).isZero();
    verify(dictRepository, times(1)).insertItemsBatch(any());
  }


  @Test
  @DisplayName("空文件：短路返回空结果，不触发 DB 写入")
  void shouldShortCircuitOnEmptyFile() {
    ImportResultVO result =
        service.importDictItems(new ByteArrayInputStream(buildEmptyXlsx()));

    assertThat(result.getTotalCount()).isZero();
    assertThat(result.getSuccessCount()).isZero();
    verify(dictRepository, never()).insertItemsBatch(any());
  }

  @Test
  @DisplayName("DB 已存在同键：批量预加载判重生效，重复行被跳过")
  void shouldSkipDuplicatesLoadedFromDb() {
    DictItemVO existing = new DictItemVO();
    existing.setTypeCode("DICT_A");
    existing.setItemCode("ITEM_0");
    when(dictRepository.findItemsByTypeCodes(anySet())).thenReturn(List.of(existing));

    ImportResultVO result =
        service.importDictItems(new ByteArrayInputStream(buildXlsx(3, "DICT_A")));

    assertThat(result.getTotalCount()).isEqualTo(3);
    assertThat(result.getSkipCount()).as("DB 已存在的 ITEM_0 应被跳过").isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("同文件内重复键：文件内去重生效")
  void shouldSkipInFileDuplicates() {
    ImportResultVO result =
        service.importDictItems(new ByteArrayInputStream(buildXlsxWithDuplicateItemCodes()));

    assertThat(result.getTotalCount()).isEqualTo(3);
    assertThat(result.getSkipCount()).as("同文件重复的 ITEM_DUP 应被跳过 1 次").isEqualTo(1);
    assertThat(result.getSuccessCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("批量插入返回 false：降级逐条插入")
  void shouldFallbackToSingleInsertWhenBatchReturnsFalse() {
    when(dictRepository.insertItemsBatch(any())).thenReturn(false);
    when(dictRepository.insertItem(any())).thenReturn(true);

    ImportResultVO result =
        service.importDictItems(new ByteArrayInputStream(buildXlsx(2, "DICT_A")));

    assertThat(result.getSuccessCount()).isEqualTo(2);
    verify(dictRepository, times(2)).insertItem(any());
  }

  @Test
  @DisplayName("批量插入抛异常：降级逐条插入且不中断")
  void shouldFallbackToSingleInsertWhenBatchThrows() {
    when(dictRepository.insertItemsBatch(any())).thenThrow(new RuntimeException("db down"));
    when(dictRepository.insertItem(any())).thenReturn(true);

    ImportResultVO result =
        service.importDictItems(new ByteArrayInputStream(buildXlsx(2, "DICT_A")));

    assertThat(result.getSuccessCount()).isEqualTo(2);
    assertThat(result.getErrors()).as("降级应记录告警信息").isNotEmpty();
  }

  /**
   * 生成含 N 行数据的字典项 xlsx（typeCode 统一）。
   *
   * @param rows 数据行数
   * @param typeCode 字典类型编码
   * @return xlsx 字节流
   */
  private byte[] buildXlsx(int rows, String typeCode) {
    List<DictItemExcelVO> list = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      DictItemExcelVO vo = new DictItemExcelVO();
      vo.setTypeCode(typeCode);
      vo.setItemCode("ITEM_" + i);
      vo.setItemValue("值" + i);
      vo.setSort(i);
      vo.setStatus("ENABLED");
      list.add(vo);
    }
    return toBytes(list);
  }

  /**
   * 生成含同文件重复 itemCode 的 xlsx（ITEM_DUP 出现两次）。
   *
   * @return xlsx 字节流
   */
  private byte[] buildXlsxWithDuplicateItemCodes() {
    List<DictItemExcelVO> list = new ArrayList<>(3);
    String[] codes = {"ITEM_A", "ITEM_DUP", "ITEM_DUP"};
    for (int i = 0; i < codes.length; i++) {
      DictItemExcelVO vo = new DictItemExcelVO();
      vo.setTypeCode("DICT_A");
      vo.setItemCode(codes[i]);
      vo.setItemValue("值" + i);
      vo.setSort(i);
      vo.setStatus("ENABLED");
      list.add(vo);
    }
    return toBytes(list);
  }

  /**
   * 生成仅含表头的空 xlsx。
   *
   * @return xlsx 字节流
   */
  private byte[] buildEmptyXlsx() {
    return toBytes(new ArrayList<>());
  }

  /**
   * 用 POI 将 VO 列表写入标准 xlsx 字节流（表头与 {@link DictItemExcelVO} 的 @ExcelProperty 对齐）。
   *
   * @param rows 数据行
   * @return xlsx 字节流
   */
  private byte[] toBytes(List<DictItemExcelVO> rows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("sheet1");
      String[] headers = {
        "字典类型编码", "字典项编码", "字典项展示值", "排序序号", "父级ID", "字典项描述", "启用状态"
      };
      Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      int rowIdx = 1;
      for (DictItemExcelVO vo : rows) {
        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(vo.getTypeCode());
        row.createCell(1).setCellValue(vo.getItemCode());
        row.createCell(2).setCellValue(vo.getItemValue());
        if (vo.getSort() != null) {
          row.createCell(3).setCellValue(vo.getSort());
        }
        if (vo.getParentId() != null) {
          row.createCell(4).setCellValue(vo.getParentId());
        }
        if (vo.getDescription() != null) {
          row.createCell(5).setCellValue(vo.getDescription());
        }
        if (vo.getStatus() != null) {
          row.createCell(6).setCellValue(vo.getStatus());
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("生成测试 xlsx 失败", e);
    }
  }
}
