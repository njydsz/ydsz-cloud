package com.njydsz.common.excel.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 高性能 CSV 写入器 — 支持 RFC 4180 转义与公式注入防护。
 *
 * <p>基于 {@link BufferedWriter} 流式输出，配合 ASM 字节码字段访问器，
 * 零 POI 依赖，低内存占用。支持 {@code @ExcelProperty} 注解驱动的自动表头与列排序。
 *
 * <h3>RFC 4180 转义规则</h3>
 * <ul>
 *   <li>含逗号、引号、换行的字段用双引号包裹</li>
 *   <li>字段内双引号转义为两个连续双引号（{@code ""}）</li>
 * </ul>
 *
 * <h3>公式注入防护（默认开启）</h3>
 * <p>以 {@code =}、{@code +}、{@code -}、{@code @} 开头的字段会被自动转义（前导撇号），
 * 与 Excel 导入 CSV 时安全展示对齐。通过 {@link #formulaInjectionProtection(boolean)} 关闭。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * CsvWriter.write(outputStream, User.class)
 *     .sheetName("用户列表")
 *     .withBom(true)
 *     .doWrite(userList);
 * }</pre>
 *
 * @param <T> 数据类型
 * @author ydsz-team
 * @since 26.09.19
 */
public class CsvWriter<T> {

  private static final Logger LOG = LoggerFactory.getLogger(CsvWriter.class);

  private static final char CSV_DELIMITER = ',';
  private static final char CSV_QUOTE = '"';
  private static final String CRLF = "\r\n";

  private final Writer writer;
  private final Class<T> clazz;
  private final List<Field> orderedFields;
  private final String[] headers;
  private final ASMFieldAccessor.FieldGetter[] getters;

  private boolean writeHeader = true;
  private boolean withBom = false;
  private boolean isFormulaInjectionProtection = true;
  private char delimiter = CSV_DELIMITER;
  private Charset charset = StandardCharsets.UTF_8;

  private CsvWriter(Writer writer, Class<T> clazz) {
    this.writer = writer;
    this.clazz = clazz;
    this.orderedFields = collectOrderedFields(clazz);
    this.headers = new String[orderedFields.size()];
    this.getters = new ASMFieldAccessor.FieldGetter[orderedFields.size()];
    for (int i = 0; i < orderedFields.size(); i++) {
      Field f = orderedFields.get(i);
      f.setAccessible(true);
      ExcelProperty ann = f.getAnnotation(ExcelProperty.class);
      String name = ann != null ? ann.value() : "";
      this.headers[i] = (name.isEmpty() ? f.getName() : name);
      this.getters[i] = ASMFieldAccessor.getGetter(clazz, f);
    }
  }

  /**
   * 创建 CSV 写入器（输出到文件路径）。
   *
   * @param path 输出文件路径
   * @param clazz 数据类型
   * @param <T> 泛型参数
   * @return CsvWriter 实例
   * @throws IOException 文件创建异常
   */
  public static <T> CsvWriter<T> write(Path path, Class<T> clazz) throws IOException {
    return new CsvWriter<>(Files.newBufferedWriter(path, StandardCharsets.UTF_8), clazz);
  }

  /**
   * 创建 CSV 写入器（输出到输出流）。
   *
   * @param outputStream 目标输出流
   * @param clazz 数据类型
   * @param <T> 泛型参数
   * @return CsvWriter 实例
   */
  public static <T> CsvWriter<T> write(OutputStream outputStream, Class<T> clazz) {
    return new CsvWriter<>(
        new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)), clazz);
  }

  /**
   * 设置是否写入 UTF-8 BOM 头。
   *
   * <p>写入 BOM 后 Excel 打开 UTF-8 CSV 不会乱码。默认 {@code false}（纯 ASCII 文件无需 BOM）。
   *
   * @param withBom {@code true} 写入 BOM
   * @return 当前写入器，便于链式调用
   */
  public CsvWriter<T> withBom(boolean withBom) {
    this.withBom = withBom;
    return this;
  }

  /**
   * 设置字段分隔符（默认为逗号 {@code ,}）。
   *
   * @param delimiter 自定义分隔符
   * @return 当前写入器，便于链式调用
   */
  public CsvWriter<T> delimiter(char delimiter) {
    this.delimiter = delimiter;
    return this;
  }

  /**
   * 设置输出字符编码（默认 UTF-8）。
   *
   * <p>注意：仅对 {@link #write(OutputStream, Class)} 入口有效；{@link #write(Path, Class)}
   * 始终使用 UTF-8。
   *
   * @param charset 目标字符编码
   * @return 当前写入器，便于链式调用
   */
  public CsvWriter<T> charset(Charset charset) {
    this.charset = charset;
    return this;
  }

  /**
   * 设置是否跳过表头行写入。
   *
   * @param writeHeader {@code false} 跳过表头行
   * @return 当前写入器，便于链式调用
   */
  public CsvWriter<T> skipHeader(boolean writeHeader) {
    this.writeHeader = !writeHeader;
    return this;
  }

  /**
   * 设置是否启用公式注入防护。
   *
   * <p>默认开启：以 {@code =}、{@code +}、{@code -}、{@code @} 开头的字段加前导撇号。
   *
   * @param enabled {@code true} 启用防护
   * @return 当前写入器，便于链式调用
   */
  public CsvWriter<T> formulaInjectionProtection(boolean enabled) {
    this.isFormulaInjectionProtection = enabled;
    return this;
  }

  /**
   * 占位方法 — 与 ExcelWriter 的 sheet() 命名保持一致（CSV 无多 Sheet），什么也不做。
   *
   * @param ignored 忽略
   * @return 当前写入器，便于链式调用
   */
  public CsvWriter<T> sheetName(String ignored) {
    return this;
  }

  /**
   * 执行 CSV 写入。
   *
   * @param data 数据列表；为 {@code null} 或空时仅写表头（若启用）
   * @throws IOException 写入异常
   */
  public void doWrite(List<T> data) throws IOException {
    if (withBom) {
      writer.write('\uFEFF');
    }
    if (writeHeader) {
      writeRow(headers);
    }
    if (data == null || data.isEmpty()) {
      writer.flush();
      return;
    }
    for (T item : data) {
      if (item == null) {
        continue;
      }
      String[] values = new String[orderedFields.size()];
      for (int i = 0; i < orderedFields.size(); i++) {
        try {
          Object raw = getters[i].get(item);
          values[i] = raw != null ? raw.toString() : "";
        } catch (Exception e) {
          LOG.warn("CSV 写入取值异常: field={}", orderedFields.get(i).getName(), e);
          values[i] = "";
        }
      }
      writeRow(values);
    }
    writer.flush();
  }

  /**
   * 写入一行原始字符串数据。
   *
   * @param values 行数据
   * @throws IOException 写入异常
   */
  private void writeRow(String[] values) throws IOException {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        writer.write(delimiter);
      }
      writer.write(escape(values[i]));
    }
    writer.write(CRLF);
  }

  /**
   * RFC 4180 字段转义。
   *
   * <p>含分隔符、引号、换行的字段用双引号包裹；内部双引号转义为 {@code ""}。
   * 公式注入防护开启时，危险前缀字段加前导撇号。
   *
   * @param value 原始值
   * @return 转义后的值
   */
  private String escape(String value) {
    if (value == null) {
      return "";
    }

    // 公式注入防护（CSV 路径）：危险前缀加前导撇号
    if (isFormulaInjectionProtection && FormulaInjectionGuard.isPotentialFormulaInjection(value)) {
      value = "'" + value;
    }

    // RFC 4180 转义
    boolean needsQuote = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == delimiter || c == CSV_QUOTE || c == '\n' || c == '\r') {
        needsQuote = true;
        break;
      }
    }

    if (!needsQuote) {
      return value;
    }

    StringBuilder sb = new StringBuilder(value.length() + 4);
    sb.append(CSV_QUOTE);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == CSV_QUOTE) {
        sb.append(CSV_QUOTE).append(CSV_QUOTE);
      } else {
        sb.append(c);
      }
    }
    sb.append(CSV_QUOTE);
    return sb.toString();
  }

  private static List<Field> collectOrderedFields(Class<?> clazz) {
    Field[] all = ReflectCache.getCachedFields(clazz);
    List<Field> annotated = new ArrayList<>(16);
    for (Field f : all) {
      if (f.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }
      if (f.isAnnotationPresent(ExcelProperty.class)) {
        annotated.add(f);
      }
    }
    annotated.sort(
        Comparator.comparingInt(f -> f.getAnnotation(ExcelProperty.class).order()));
    return annotated;
  }
}
