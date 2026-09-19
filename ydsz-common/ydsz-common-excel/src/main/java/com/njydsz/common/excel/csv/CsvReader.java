package com.njydsz.common.excel.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 流式 CSV 读取器 — 支持 BOM 识别、RFC 4180 转义与注解映射。
 *
 * <p>逐行流式读取，内存中仅保留当前行数据，适合大型 CSV 文件。
 * 首行默认为表头行，随后逐行映射为 Java 对象。
 *
 * <h3>BOM 处理</h3>
 * <p>自动识别并跳过 UTF-8 BOM（{@code EF BB BF}）。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 流式读取（适合大文件）
 * CsvReader.read(inputStream, User.class)
 *     .sheetName("任意名称")
 *     .doRead(user -> service.save(user));
 *
 * // 全部读入内存
 * List<User> users = CsvReader.read(path, User.class).doReadAll();
 * }</pre>
 *
 * @param <T> 数据类型
 * @author ydsz-team
 * @since 26.09.19
 */
public class CsvReader<T> {

  private static final Logger LOG = LoggerFactory.getLogger(CsvReader.class);

  private static final char CSV_DELIMITER = ',';
  private static final char CSV_QUOTE = '"';
  private static final char BOM_CHAR = '\uFEFF';

  private final Reader reader;
  private final Class<T> clazz;
  private final List<Field> orderedFields;
  private final ASMFieldAccessor.FieldSetter[] setters;
  private final String[] fieldDateFormats;

  private char delimiter = CSV_DELIMITER;
  private boolean skipEmptyRows = true;
  private int headRowNumber = 1;

  private CsvReader(Reader reader, Class<T> clazz) {
    this.reader = reader;
    this.clazz = clazz;
    this.orderedFields = collectOrderedFields(clazz);
    this.setters = new ASMFieldAccessor.FieldSetter[orderedFields.size()];
    this.fieldDateFormats = new String[orderedFields.size()];
    for (int i = 0; i < orderedFields.size(); i++) {
      Field f = orderedFields.get(i);
      f.setAccessible(true);
      ExcelProperty ann = f.getAnnotation(ExcelProperty.class);
      this.fieldDateFormats[i] = ann != null ? ann.dateFormat() : "";
      this.setters[i] = ASMFieldAccessor.getSetter(clazz, f);
    }
  }

  /**
   * 从文件路径创建 CSV 读取器。
   *
   * @param path CSV 文件路径
   * @param clazz 目标类型
   * @param <T> 泛型参数
   * @return CsvReader 实例
   * @throws IOException 文件打开异常
   */
  public static <T> CsvReader<T> read(Path path, Class<T> clazz) throws IOException {
    return new CsvReader<>(Files.newBufferedReader(path, StandardCharsets.UTF_8), clazz);
  }

  /**
   * 从输入流创建 CSV 读取器（UTF-8 编码）。
   *
   * @param inputStream 输入流
   * @param clazz 目标类型
   * @param <T> 泛型参数
   * @return CsvReader 实例
   */
  public static <T> CsvReader<T> read(InputStream inputStream, Class<T> clazz) {
    return new CsvReader<>(
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)), clazz);
  }

  /**
   * 从输入流创建 CSV 读取器（指定字符编码）。
   *
   * @param inputStream 输入流
   * @param charset 字符编码
   * @param clazz 目标类型
   * @param <T> 泛型参数
   * @return CsvReader 实例
   */
  public static <T> CsvReader<T> read(InputStream inputStream, Charset charset, Class<T> clazz) {
    return new CsvReader<>(new BufferedReader(new InputStreamReader(inputStream, charset)), clazz);
  }

  /**
   * 设置字段分隔符（默认为逗号 {@code ,}）。
   *
   * @param delimiter 自定义分隔符
   * @return 当前读取器，便于链式调用
   */
  public CsvReader<T> delimiter(char delimiter) {
    this.delimiter = delimiter;
    return this;
  }

  /**
   * 设置是否跳过空行（默认 {@code true}）。
   *
   * @param skipEmptyRows {@code true} 跳过空行
   * @return 当前读取器，便于链式调用
   */
  public CsvReader<T> skipEmptyRows(boolean skipEmptyRows) {
    this.skipEmptyRows = skipEmptyRows;
    return this;
  }

  /**
   * 占位方法 — 与 ExcelReader 的 headRowNumber() 命名保持一致（CSV 首行固定为表头），什么也不做。
   *
   * @param ignored 忽略
   * @return 当前读取器，便于链式调用
   */
  public CsvReader<T> headRowNumber(int ignored) {
    return this;
  }

  /**
   * 占位方法 — 与 ExcelReader 的 sheet() 命名保持一致（CSV 无 Sheet 概念），什么也不做。
   *
   * @param ignored 忽略
   * @return 当前读取器，便于链式调用
   */
  public CsvReader<T> sheetName(String ignored) {
    return this;
  }

  /**
   * 流式读取，逐行回调消费者。
   *
   * <p>适合大文件场景；内存中仅保留当前行数据。首行作为表头自动跳过（不做映射对象处理）。
   *
   * @param consumer 每行数据消费者；为 {@code null} 时仅遍历不消费
   * @throws IOException 读取异常
   */
  public void doRead(Consumer<T> consumer) throws IOException {
    BufferedReader br = new BufferedReader(reader);
    String line;

    // 跳过 BOM（首行首字符）
    br.mark(1);
    int firstChar = br.read();
    if (firstChar != -1 && (char) firstChar != BOM_CHAR) {
      br.reset();
    }

    // 首行作为表头行：跳过（注解映射模式下不解析表头名→字段的匹配）
    br.readLine();

    while ((line = br.readLine()) != null) {
      if (skipEmptyRows && line.isBlank()) {
        continue;
      }
      String[] values = parseLine(line);
      if (consumer != null) {
        consumer.accept(mapToRow(values));
      }
    }
  }

  /**
   * 全部读入内存后返回列表。
   *
   * <p>适合中小文件场景（能完全放入内存）。首行作为表头自动跳过。
   *
   * @return 数据列表，永不为 {@code null}
   * @throws IOException 读取异常
   */
  public List<T> doReadAll() throws IOException {
    List<T> result = new ArrayList<>(128);
    doRead(result::add);
    return result;
  }

  /**
   * 将 CSV 行映射为 Java 对象。
   *
   * <p>按字段声明顺序逐列设置值。列数多于字段数时忽略多余列；列数少于字段数时剩余字段保持默认值。
   *
   * @param values 列值数组
   * @return 映射后的对象
   */
  private T mapToRow(String[] values) {
    try {
      T instance = clazz.getDeclaredConstructor().newInstance();
      int n = Math.min(values.length, orderedFields.size());
      for (int i = 0; i < n; i++) {
        String v = values[i];
        if (v.isEmpty()) {
          continue;
        }
        Field f = orderedFields.get(i);
        Object converted = DefaultAnnotationRowMapper.convert(v, f.getType(), fieldDateFormats[i]);
        setters[i].set(instance, converted);
      }
      return instance;
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to instantiate " + clazz.getName() + " from CSV row", e);
    }
  }

  /**
   * RFC 4180 行解析（支持带引号字段的转义处理）。
   *
   * @param line 原始行文本
   * @return 列值数组
   */
  static String[] parseLine(String line) {
    if (line == null || line.isEmpty()) {
      return new String[0];
    }

    List<String> fields = new ArrayList<>(16);
    StringBuilder current = new StringBuilder(64);
    boolean inQuotes = false;
    int i = 0;
    int len = line.length();

    while (i < len) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == CSV_QUOTE) {
          // 连续两个引号 → 转义为一个引号
          if (i + 1 < len && line.charAt(i + 1) == CSV_QUOTE) {
            current.append(CSV_QUOTE);
            i += 2;
          } else {
            inQuotes = false;
            i++;
          }
        } else {
          current.append(c);
          i++;
        }
      } else {
        if (c == CSV_QUOTE) {
          inQuotes = true;
          i++;
        } else if (c == CSV_DELIMITER) {
          fields.add(current.toString());
          current.setLength(0);
          i++;
        } else {
          current.append(c);
          i++;
        }
      }
    }
    fields.add(current.toString());
    return fields.toArray(new String[0]);
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
