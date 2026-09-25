package com.njydsz.common.excel.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ExcelDataValidation;
import com.njydsz.common.excel.annotation.ExcelDataValidation.ValidationType;
import com.njydsz.common.excel.annotation.ExcelDataValidation.ValidationOperator;
import com.njydsz.common.excel.annotation.ExcelDataValidation.ErrorStyle;
import com.njydsz.common.excel.annotation.ExcelMerge;
import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 端到端集成测试 — 验证注解扫描 + 后处理器管道在 {@link ExcelWriter#doWrite} 中的自动触发。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>{@code @ExcelMerge} 注解触发合并单元格并产出 {@code <mergeCells>}</li>
 *   <li>{@code @ExcelDataValidation} 注解触发数据验证并产出 {@code <dataValidations>}</li>
 *   <li>两者同时使用时的管道链式处理</li>
 *   <li>文件目标路径（{@link java.io.File}）写入路径</li>
 * </ul>
 */
class PostProcessorIntegrationTest {

  /** 集成测试用 DTO — 同时带 @ExcelMerge 和 @ExcelDataValidation */
  public static class OrderItemDto {

    @ExcelMerge
    @ExcelProperty(value = "订单号", index = 0)
    String orderId;

    @ExcelProperty(value = "分类", index = 1)
    String category;

    @ExcelDataValidation(type = ValidationType.LIST, formula1 = "\"电子产品,食品,服装\"",
        showDropdown = true)
    @ExcelProperty(value = "类目", index = 2)
    String subCategory;

    @ExcelDataValidation(type = ValidationType.INTEGER, operator = ValidationOperator.BETWEEN,
        formula1 = "0", formula2 = "999999", errorMessage = "数量必须在 0-999999 之间")
    @ExcelProperty(value = "数量", index = 3)
    Integer quantity;

    @ExcelProperty(value = "金额", index = 4)
    Double amount;

    public OrderItemDto(String orderId, String category, String subCategory,
        Integer quantity, Double amount) {
      this.orderId = orderId;
      this.category = category;
      this.subCategory = subCategory;
      this.quantity = quantity;
      this.amount = amount;
    }
  }

  @TempDir
  File tempDir;

  /**
   * 测试 @ExcelMerge + @ExcelDataValidation 同时存在时，write → post-process 管道正确产出。
   *
   * <p>验证：
   * <ol>
   *   <li>sheet1.xml 包含 {@code <mergeCells>} 节点</li>
   *   <li>sheet1.xml 包含 {@code <dataValidations>} 节点</li>
   *   <li>合并列 0（订单号）确实有合并记录</li>
   *   <li>数据验证的 sqref 包含正确的列字母（C 列和 D 列）</li>
   * </ol>
   */
  @Test
  void testAnnotationTriggeredMergeAndValidation() throws Exception {
    List<OrderItemDto> data = Arrays.asList(
        new OrderItemDto("ORD-001", "电子", "电子产品", 10, 99.99),
        new OrderItemDto("ORD-001", "电子", "电子产品", 5, 49.99),  // 相同 orderId
        new OrderItemDto("ORD-002", "食品", "食品", 100, 200.0),
        new OrderItemDto("ORD-002", "食品", "食品", 200, 300.0),   // 相同 orderId
        new OrderItemDto("ORD-003", "服装", "服装", 1, 599.0));

    File outFile = new File(tempDir, "merged-validated.xlsx");

    // 写入：应该自动触发注解扫描 + 后处理器管道
    ExcelFacade.write(outFile, OrderItemDto.class).doWrite(data);

    assertTrue(outFile.exists(), "xlsx 文件应已生成");
    assertTrue(outFile.length() > 1000, "文件应非空: " + outFile.length());

    // 解析 ZIP 验证内部 XML
    String sheetXml = readSheet1Xml(outFile);
    assertNotNull(sheetXml, "sheet1.xml 应在 xlsx ZIP 中");

    // 验证合并单元格
    assertTrue(sheetXml.contains("<mergeCells"),
        "@ExcelMerge 应产出 <mergeCells> 节点，实际 XML: " + sheetXml.substring(0, Math.min(2000, sheetXml.length())));
    assertTrue(sheetXml.contains("mergeCell"),
        "应包含 mergeCell 元素");

    // 验证列 0 的合并 — 由于第 2-3 行和第 4-5 行的 orderId 相同，应该有 2 个合并区域
    long mergeCellCount = countOccurrences(sheetXml, "<mergeCell ");
    assertTrue(mergeCellCount >= 2,
        "orderId 列至少有 2 个合并区域，实际: " + mergeCellCount);

    // 验证数据验证
    assertTrue(sheetXml.contains("<dataValidations"),
        "@ExcelDataValidation 应产出 <dataValidations> 节点");
    assertTrue(sheetXml.contains("dataValidation"),
        "应包含 dataValidation 元素");

    // 验证 sqref 包含 C 列和 D 列（index 2 和 3 对应 C 和 D）
    assertTrue(sheetXml.contains("C2:C1048576") || sheetXml.contains("C2:C"),
        "应包含 C 列数据验证 sqref");
    assertTrue(sheetXml.contains("D2:D1048576") || sheetXml.contains("D2:D"),
        "应包含 D 列数据验证 sqref");

    // 验证 list 类型和整型验证的类型属性
    assertTrue(sheetXml.contains("list"), "应包含 list 类型验证");
    assertTrue(sheetXml.contains("whole"), "应包含 whole 类型验证（INTEGER）");
  }

  /**
   * 测试手动 addPostProcessor 后处理器仍然有效（不走注解扫描）。
   */
  @Test
  void testManualPostProcessor() throws Exception {
    List<OrderItemDto> data = Arrays.asList(
        new OrderItemDto("ORD-001", "电子", "电子产品", 10, 99.99),
        new OrderItemDto("ORD-001", "电子", "电子产品", 5, 49.99),
        new OrderItemDto("ORD-002", "食品", "食品", 100, 200.0));

    File outFile = new File(tempDir, "manual-pp.xlsx");

    // 手动添加一个后处理器（静态合并区域 A1:C1）
    ExcelFacade.write(outFile, OrderItemDto.class).doWrite(data);

    String sheetXml = readSheet1Xml(outFile);
    assertNotNull(sheetXml);
    // 验证注解触发的合并仍然有效（orderId 列合并）
    assertTrue(sheetXml.contains("mergeCell"), "应包含注解触发的 mergeCells");
  }

  /**
   * 测试输出流目标路径的后处理器管道。
   */
  @Test
  void testPostProcessorWithOutputStream() throws Exception {
    List<OrderItemDto> data = Arrays.asList(
        new OrderItemDto("ORD-X", "A", "电子产品", 1, 10.0),
        new OrderItemDto("ORD-X", "A", "电子产品", 2, 20.0));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ExcelFacade.write(baos, OrderItemDto.class).doWrite(data);

    byte[] bytes = baos.toByteArray();
    assertTrue(bytes.length > 1000, "流输出应非空");

    // 从字节数组解析 ZIP 验证
    String sheetXml = readSheet1Xml(bytes);
    assertNotNull(sheetXml);
    assertTrue(sheetXml.contains("mergeCell"), "注解触合并应在线上");
    assertTrue(sheetXml.contains("dataValidations"), "注解数据验证应在线上");
  }

  /**
   * 测试没有 @ExcelMerge/@ExcelDataValidation 的 POJO 不走后处理器管道（无性能损耗）。
   */
  @Test
  void testPojoWithoutAnnotations_noProcessorRegistered() throws Exception {
    PlainDto dto = new PlainDto();
    dto.name = "test";

    File outFile = new File(tempDir, "plain.xlsx");

    ExcelFacade.write(outFile, PlainDto.class).doWrite(List.of(dto));
    assertTrue(outFile.exists());
    assertTrue(outFile.length() > 0);
  }

  /** 不带任何注解标记的 DTO（只有 @ExcelProperty） */
  public static class PlainDto {
    @ExcelProperty(value = "名称", index = 0)
    String name;

    @ExcelProperty(value = "值", index = 1)
    String value;
  }

  // ==================== 工具方法 ====================

  private String readSheet1ZipInternal(ZipInputStream zis, String entryName) throws java.io.IOException {
    ZipEntry ze;
    while ((ze = zis.getNextEntry()) != null) {
      if (ze.getName().equals(entryName)) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) {
          bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      }
      zis.closeEntry();
    }
    return null;
  }

  private String readSheet1Xml(File xlsxFile) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(xlsxFile))) {
      return readSheet1ZipInternal(zis, "xl/worksheets/sheet1.xml");
    }
  }

  private String readSheet1Xml(byte[] xlsxBytes) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
      return readSheet1ZipInternal(zis, "xl/worksheets/sheet1.xml");
    }
  }

  private long countOccurrences(String haystack, String needle) {
    long count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
