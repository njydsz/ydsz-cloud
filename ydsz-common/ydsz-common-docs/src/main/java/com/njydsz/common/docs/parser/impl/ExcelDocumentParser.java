package com.njydsz.common.docs.parser.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.DocumentTable;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.RawSheetData;

/**
 * Excel 文档解析器（.xlsx / .xls）
 *
 * <p>基于 {@link ExcelFacade#readAllSheets} 解析 Excel 文档，提取所有 Sheet 的表格数据。 统一处理 HSSF/XSSF
 * 格式识别、空行过滤、单元格值转字符串， 消除直接使用 Apache POI DOM 模式（{@code WorkbookFactory.create}）的重复编码。
 *
 * <p>第一行作为表头，后续行作为数据行。全空行自动过滤。 单元格值统一转为字符串，日期按 {@code yyyy-MM-dd HH:mm:ss} 格式输出， 数字为整数时去掉小数部分。
 *
 * <p><b>页码语义借用：</b>Excel 无自然分页，此处将 Sheet 序号（从 1 起） 填入 {@code pageNumber}，{@code totalPages} 等于
 * Sheet 总数， 便于上层用统一的"按页定位"逻辑回溯内容来源。
 *
 * <p><b>两处刻意的行为：</b>一是全空行被丢弃（POI 会把格式化过的空行也算作行， 保留会污染文本与行数统计）；二是 {@code sections} 恒为空列表—— Excel
 * 内容已由 {@code tables} 完整承载，再拆分节没有语义收益。 因此需要文档结构分节的下游（如分块器）对 Excel 结果只能依赖 {@code text}。
 *
 * <p>整个工作簿以 DOM 方式载入内存，大文件内存占用约为文件体积的数倍， 需由上层按 {@code maxFileSizeMb} 提前拦截。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.njydsz.common.excel.core.ExcelFacade")
public class ExcelDocumentParser implements DocumentParser {

  /**
   * 遍历工作簿全部 Sheet，将每张表提取为一个 {@link DocumentTable}。
   *
   * <p>委托 {@link ExcelFacade#readAllSheets} 统一处理格式识别、空行过滤与单元格值转换， 本方法仅负责将 {@link RawSheetData} 转换为
   * {@link DocumentContent} 领域对象。
   *
   * @param inputStream Excel 字节流，由调用方负责关闭；为 {@code null} 时视为空文档
   * @param fileName 原始文件名，仅写入元数据标题用于展示与排障
   * @param options 解析选项，本实现未使用，可传 {@code null}
   * @return 文档内容，每张非空 Sheet 对应一张表格，纯文本以 {@code === Sheet名 ===} 分隔各表
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}； 读取失败时错误码 {@code
   *     PARSE_FAILED}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    try {
      List<RawSheetData> sheets = ExcelFacade.readAllSheets(inputStream);
      List<DocumentTable> tables = new ArrayList<>(16);
      List<DocumentSection> sections = new ArrayList<>(16);
      StringBuilder fullText = new StringBuilder();

      for (RawSheetData sheet : sheets) {
        if (!sheet.hasData()) {
          continue;
        }

        int maxCols = sheet.columnCount();
        tables.add(
            DocumentTable.builder()
                .caption(sheet.sheetName())
                .pageNumber(tables.size() + 1)
                .rowCount(sheet.rows().size())
                .colCount(maxCols)
                .rows(sheet.rows())
                .build());

        // 追加文本
        fullText.append("=== ").append(sheet.sheetName()).append(" ===\n");
        for (List<String> row : sheet.rows()) {
          fullText.append(String.join("\t", row)).append('\n');
        }
        fullText.append('\n');
      }

      String text = fullText.toString();
      int totalPages = sheets.size();
      return DocumentContent.builder()
          .text(text)
          .sections(sections)
          .tables(tables)
          .metadata(DocumentMetadata.builder().title(fileName).charCount(text.length()).build())
          .totalChars(text.length())
          .totalPages(totalPages)
          .build();

    } catch (DocumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("[ExcelDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    }
  }

  /**
   * 声明本解析器在注册中心占据的主格式槽位。
   *
   * <p>返回值仅代表注册键，实际受理范围以 {@link #supports(DocumentFormat)} 为准， 二者不等价：本类同时能处理旧版 XLS。
   *
   * @return 恒为 {@link DocumentFormat#XLSX}
   */
  @Override
  public DocumentFormat getSupportedFormat() {
    return DocumentFormat.XLSX;
  }

  /**
   * 判定是否受理指定格式，覆写以扩大到新旧两代 Excel。
   *
   * <p>覆盖父接口"仅等值匹配主格式"的默认实现：因为 {@link ExcelFacade#readAllSheets} 已屏蔽 HSSF/XSSF 差异，一套代码即可通吃，无需为 XLS
   * 单独再写一个解析器。
   *
   * <p>注意含宏的 {@link DocumentFormat#XLSM} <b>不在此列</b>， 需先经安全扫描器确认无害后由上层显式决策，避免宏文档被静默解析。
   *
   * @param format 待判定的文档格式，可为 {@code null}（返回 {@code false}）
   * @return 格式为 XLSX 或 XLS 时返回 {@code true}
   */
  @Override
  public boolean supports(DocumentFormat format) {
    return format == DocumentFormat.XLSX || format == DocumentFormat.XLS;
  }
}
