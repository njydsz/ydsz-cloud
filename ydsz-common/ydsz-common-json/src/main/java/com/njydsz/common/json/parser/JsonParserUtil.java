package com.njydsz.common.json.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * YdszJson 底层 JSON 解析器（零依赖，JIT + 循环展开 优化版）
 *
 * <p>直接解析 JSON 字符串为 Map/List 结构，不依赖 YdszJson。
 *
 * <p><b>JIT 优化：</b>
 *
 * <ul>
 *   <li>所有方法使用 final 修饰，避免虚方法调用
 *   <li>热点方法内联（< 35 字节码）
 *   <li>使用 switch 表达式优化分支预测
 *   <li>避免同步锁，使用无锁设计
 * </ul>
 *
 * <p><b>循环展开 优化：</b>
 *
 * <ul>
 *   <li>向量化空白字符检测
 *   <li>批量字符串比较
 *   <li>零拷贝字符串提取
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 解析 JSON 对象
 * Map&lt;String, Object&gt; map = JsonParserUtil.parseObject(json);
 *
 * // 解析 JSON 数组
 * List&lt;Object&gt; list = JsonParserUtil.parseArray(json);
 *
 * // 解析为 Object（自动识别）
 * Object obj = JsonParserUtil.parse(json);
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class JsonParserUtil {

  /** 预计算的10的幂次表大小（覆盖 0~23 位小数）。 */
  private static final int POW10_TABLE_SIZE = 24;

  /** 字符数组缓冲区初始大小（8KB）。 */
  private static final int CHAR_BUFFER_SIZE = 8192;

  /** StringBuilder 对象池初始容量。 */
  private static final int SB_POOL_INIT_CAPACITY = 256;

  /** 默认最大递归解析深度。 */
  private static final int DEFAULT_MAX_PARSE_DEPTH = 256;

  /** 空 Map 初始容量。 */
  private static final int EMPTY_MAP_CAPACITY = 8;

  /** 对象解析默认初始容量。 */
  private static final int PARSE_OBJECT_INITIAL_CAPACITY = 64;

  /** 空 List 初始容量。 */
  private static final int EMPTY_LIST_CAPACITY = 8;

  /**
   * 预计算 10 的幂次表（替代 Math.pow(10, n)，避免浮点函数调用开销）。
   *
   * <p>覆盖 0~23 位小数（double 精度上限为 15-17 位有效数字， 超过 22 位时 parseNumberFast 已回退到 Double.parseDouble）。
   */
  private static final double[] POW10 = new double[POW10_TABLE_SIZE];

  static {
    POW10[0] = 1.0;
    for (int i = 1; i < POW10.length; i++) {
      POW10[i] = POW10[i - 1] * 10.0;
    }
  }

  /**
   * 字符数组缓存（ThreadLocal 复用）
   *
   * <p>解析器内部缓冲区，用于临时字符操作。与 {@link SerializationContext} 分离， 因为 SerializationContext 仅用于序列化路径，而
   * CHAR_BUFFER 用于反序列化路径。
   *
   * <p>线程池环境下应调用 {@link #clearThreadLocals()} 清理，防止内存泄漏。
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<char[]> CHAR_BUFFER =
  // CHECKSTYLE.ON: RegexpSinglelineJava
      ThreadLocal.withInitial(() -> new char[CHAR_BUFFER_SIZE]);

  /**
   * StringBuilder 对象池（ThreadLocal 复用）
   *
   * <p>解析器内部缓冲区，用于构建解析结果字符串。与 {@link SerializationContext} 分离， 因为 SerializationContext 仅用于序列化路径，而
   * SB_POOL 用于反序列化路径。
   *
   * <p>线程池环境下应调用 {@link #clearThreadLocals()} 清理，防止内存泄漏。
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<StringBuilder> SB_POOL =
  // CHECKSTYLE.ON: RegexpSinglelineJava
      ThreadLocal.withInitial(() -> new StringBuilder(SB_POOL_INIT_CAPACITY));

  /** 是否使用 BigDecimal 解析浮点数（避免精度丢失），默认 false（按线程隔离，避免跨线程泄漏） */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<Boolean> USE_BIG_DECIMAL = ThreadLocal.withInitial(() -> false);
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /** 递归解析最大嵌套深度（防止栈溢出攻击），与 JSONReader.DEFAULT_MAX_DEPTH 对齐，默认 256 */
  private static volatile int maxParseDepth = DEFAULT_MAX_PARSE_DEPTH;

  /**
   * 线程级解析深度覆盖（P0-3 修复：多 Mapper 实例隔离）。
   *
   * <p>{@code JsonMapper} 调用期间将自身 maxDepth 写入本覆盖， {@link #resolveMaxParseDepth()}
   * 优先读取，避免静态全局值被多实例互相覆盖。 与 {@code JSONReader#setCallDepthOverride} 保持同一套语义。
   *
   * @since 26.09.01
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<Integer> CALL_PARSE_DEPTH = new ThreadLocal<>();
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /**
   * 设置全局递归解析最大嵌套深度（默认 256，与 JSONReader 对齐）。
   *
   * @param depth 递归解析最大嵌套深度
   */
  public static void setMaxParseDepth(int depth) {
    if (depth <= 0) {
      throw new IllegalArgumentException("maxParseDepth must be > 0, got: " + depth);
    }
    maxParseDepth = depth;
  }

  /**
   * 获取当前递归解析最大嵌套深度。
   *
   * @return 当前递归解析最大嵌套深度
   */
  public static int getMaxParseDepth() {
    return maxParseDepth;
  }

  /**
   * 设置线程级解析深度覆盖（框架内部使用，供 JsonMapper 调用期间隔离实例配置）。
   *
   * @param depth 覆盖值（null 清除覆盖，回退静态全局值）
   * @since 26.09.01
   */
  public static void setCallParseDepthOverride(Integer depth) {
    if (depth != null) {
      if (depth <= 0) {
        throw new IllegalArgumentException("callParseDepth must be > 0, got: " + depth);
      }
      CALL_PARSE_DEPTH.set(depth);
    } else {
      CALL_PARSE_DEPTH.remove();
    }
  }

  /**
   * 获取当前线程的解析深度覆盖值（未设置返回 null，框架内部使用）。
   *
   * @return 覆盖值，未设置返回 null
   * @since 26.09.01
   */
  public static Integer getCallParseDepthOverride() {
    return CALL_PARSE_DEPTH.get();
  }

  /**
   * 解析当前生效的递归解析深度（优先线程级覆盖，P0-3）。
   *
   * @return 生效的最大解析深度
   * @since 26.09.01
   */
  static int resolveMaxParseDepth() {
    Integer callDepth = CALL_PARSE_DEPTH.get();
    return callDepth != null ? callDepth : maxParseDepth;
  }

  private JsonParserUtil() {
    throw new UnsupportedOperationException("JsonParserUtil is a utility class");
  }

  /** 清理所有 ThreadLocal 变量（防止线程池环境内存泄漏）。 */
  public static void clearThreadLocals() {
    CHAR_BUFFER.remove();
    SB_POOL.remove();
    USE_BIG_DECIMAL.remove();
    CALL_PARSE_DEPTH.remove();
  }

  /**
   * 解析 JSON 为 Object（Map 或 List）
   *
   * @param json JSON 字符串
   * @return Map 或 List
   */
  public static Object parse(String json) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }

    json = json.trim();
    if (json.startsWith("{")) {
      return parseObject(json);
    } else if (json.startsWith("[")) {
      return parseArray(json);
    } else {
      throw new JsonDeserializationException("Invalid JSON: " + json);
    }
  }

  /**
   * 解析 JSON 对象
   *
   * <p>直接创建结果 Map，不再使用 ThreadLocal 对象池。 原对象池实现每次解析都会 new HashMap(64) 赋值给 ThreadLocal， 与直接创建结果 Map
   * 产生相同的 GC 压力，还额外增加 ThreadLocal 开销。
   *
   * @param json JSON 字符串
   * @return Map 对象
   */
  public static Map<String, Object> parseObject(String json) {
    if (json == null || json.isEmpty()) {
      return new HashMap<>(EMPTY_MAP_CAPACITY);
    }

    char[] chars = getCharBuffer(json);
    int len = json.length();

    // 跳过前导空白
    int startPos = 0;
    while (startPos < len && chars[startPos] <= ' ') {
      startPos++;
    }

    if (startPos >= len || chars[startPos] != '{') {
      if (startPos >= len) {
        return new HashMap<>(EMPTY_MAP_CAPACITY);
      }
      throw new JsonDeserializationException(
          "Invalid JSON object: expected '{' at position " + startPos, startPos);
    }

    // 委托给 parseObjectRecursiveImpl 统一实现（参数化初始容量 64）
    Object result = parseObjectRecursiveImpl(chars, startPos, PARSE_OBJECT_INITIAL_CAPACITY, 1);
    @SuppressWarnings("unchecked") // @SuppressWarnings 保留原因：泛型擦除，parseObjectRecursiveImpl() 返回 Object，强转 Map 编译期无法验证
    Map<String, Object> map = (Map<String, Object>) result;
    return map;
  }

  /**
   * 解析 JSON 数组
   *
   * <p>直接创建结果 List，不再使用 ThreadLocal 对象池。
   *
   * @param json JSON 字符串
   * @return List 对象
   */
  public static List<Object> parseArray(String json) {
    if (json == null || json.trim().isEmpty()) {
      return new ArrayList<>(EMPTY_LIST_CAPACITY);
    }

    json = json.trim();
    if (json.length() < 2 || json.charAt(0) != '[') {
      throw new JsonDeserializationException("Invalid JSON array: " + json);
    }

    char[] chars = getCharBuffer(json);
    // 委托给 parseArrayRecursiveImpl 统一实现，消除重复代码
    @SuppressWarnings("unchecked") // @SuppressWarnings 保留原因：泛型擦除，parseArrayRecursiveImpl() 返回 Object，强转 List 编译期无法验证
    List<Object> result = (List<Object>) parseArrayRecursiveImpl(chars, 0, 1);
    return result;
  }

  /** 解析 JSON 值（优化版 - 关键路径内联） */
  /**
   * 解析 JSON 值（返回解析后的位置，消除调用方二次扫描）。
   *
   * @param chars 字符数组
   * @param pos 起始位置
   * @param endPos [out] 解析结束位置（值的下一个字符位置）
   * @param depth 当前解析深度
   * @return 解析后的值
   */
  private static Object parseValueWithPos(char[] chars, int pos, int[] endPos, int depth) {
    // 快速路径：跳过空白（内联）
    pos = skipWhitespace(chars, pos);

    if (pos >= chars.length) {
      endPos[0] = pos;
      return null;
    }

    char c = chars[pos];

    if (c == '"') {
      return parseStringFastWithPos(chars, pos, endPos);
    }
    if (c == '{') {
      return parseObjectRecursiveWithPos(chars, pos, endPos, depth + 1);
    }
    if (c == '[') {
      return parseArrayRecursiveWithPos(chars, pos, endPos, depth + 1);
    }
    if (c == 't') {
      return parseTrueLiteral(chars, pos, endPos);
    }
    if (c == 'f') {
      return parseFalseLiteral(chars, pos, endPos);
    }
    if (c == 'n') {
      return parseNullLiteral(chars, pos, endPos);
    }
    if (c == '-' || (c >= '0' && c <= '9')) {
      return parseNumberFastWithPos(chars, pos, endPos);
    }
    throw new JsonDeserializationException(
        "Unexpected character at position " + pos + ": " + c, pos);
  }

  /**
   * 解析 JSON true 字面量。
   *
   * @param chars 字符数组
   * @param pos 起始位置（指向字符 't'）
   * @param endPos [out] 解析结束位置
   * @return Boolean.TRUE
   */
  private static Boolean parseTrueLiteral(char[] chars, int pos, int[] endPos) {
    if (pos + 3 < chars.length
        && chars[pos + 1] == 'r'
        && chars[pos + 2] == 'u'
        && chars[pos + 3] == 'e') {
      endPos[0] = pos + 4;
      return Boolean.TRUE;
    }
    throw new JsonDeserializationException(
        "Unexpected token starting with 't' at position " + pos, pos);
  }

  /**
   * 解析 JSON false 字面量。
   *
   * @param chars 字符数组
   * @param pos 起始位置（指向字符 'f'）
   * @param endPos [out] 解析结束位置
   * @return Boolean.FALSE
   */
  private static Boolean parseFalseLiteral(char[] chars, int pos, int[] endPos) {
    if (pos + 4 < chars.length
        && chars[pos + 1] == 'a'
        && chars[pos + 2] == 'l'
        && chars[pos + 3] == 's'
        && chars[pos + 4] == 'e') {
      endPos[0] = pos + 5;
      return Boolean.FALSE;
    }
    throw new JsonDeserializationException(
        "Unexpected token starting with 'f' at position " + pos, pos);
  }

  /**
   * 解析 JSON null 字面量。
   *
   * @param chars 字符数组
   * @param pos 起始位置（指向字符 'n'）
   * @param endPos [out] 解析结束位置
   * @return null
   */
  private static Object parseNullLiteral(char[] chars, int pos, int[] endPos) {
    if (pos + 3 < chars.length
        && chars[pos + 1] == 'u'
        && chars[pos + 2] == 'l'
        && chars[pos + 3] == 'l') {
      endPos[0] = pos + 4;
      return null;
    }
    throw new JsonDeserializationException(
        "Unexpected token starting with 'n' at position " + pos, pos);
  }

  /** 兼容旧调用方（委托给 withPos 版本） */
  private static Object parseValue(char[] chars, int pos) {
    int[] endPos = new int[1];
    return parseValueWithPos(chars, pos, endPos, 1);
  }

  /** 快速解析字符串（返回值和结束位置） */
  private static String parseStringFastWithPos(char[] chars, int pos, int[] endPos) {
    int len = chars.length;
    pos++; // 跳过起始引号

    int start = pos;
    boolean hasEscape = false;

    while (pos < len) {
      char c = chars[pos];
      if (c == '"') {
        endPos[0] = pos + 1; // 结束引号后
        if (!hasEscape) {
          return new String(chars, start, pos - start);
        } else {
          return parseStringWithEscape(chars, start, pos);
        }
      } else if (c == '\\') {
        hasEscape = true;
        pos++;
      }
      pos++;
    }

    throw new JsonDeserializationException("Unterminated string", pos);
  }

  /** 兼容旧调用方 */
  private static String parseStringFast(char[] chars, int pos) {
    int[] endPos = new int[1];
    return parseStringFastWithPos(chars, pos, endPos);
  }

  /** 解析带转义的字符串 */
  private static String parseStringWithEscape(char[] chars, int start, int end) {
    int len = end - start;
    StringBuilder sb = SB_POOL.get();
    sb.setLength(0);
    if (sb.capacity() < len) {
      sb.ensureCapacity(len);
    }
    int pos = start;

    while (pos < end) {
      char c = chars[pos];
      if (c == '\\') {
        pos++;
        if (pos >= end) {
          throw new JsonDeserializationException("Unexpected end of string", pos);
        }
        char escaped = chars[pos];
        switch (escaped) {
          case '"':
            sb.append('"');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '/':
            sb.append('/');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'u':
            if (pos + 4 >= end) {
              throw new JsonDeserializationException(
                  "Invalid unicode escape at position " + pos, pos);
            }
            String hex = new String(chars, pos + 1, 4);
            sb.append((char) Integer.parseInt(hex, 16));
            pos += 4;
            break;
          default:
            sb.append(escaped);
        }
      } else {
        sb.append(c);
      }
      pos++;
    }

    return sb.toString();
  }

  /**
   * 设置是否使用 BigDecimal 解析浮点数。
   *
   * <p>启用后，包含小数点的数字将被解析为 {@link BigDecimal}， 避免金融场景下的精度丢失。
   *
   * @param enabled true 表示使用 BigDecimal
   * @author ydsz-team
   * @since 26.09.01
   */
  public static void setUseBigDecimal(boolean enabled) {
    USE_BIG_DECIMAL.set(enabled);
  }

  /**
   * 查询当前线程是否使用 BigDecimal 解析浮点数。
   *
   * @return 当前线程已开启 {@code BigDecimal} 浮点解析时返回 {@code true}；
   *     该开关按线程隔离（{@link ThreadLocal}），未显式设置时默认 {@code false}
   */
  public static boolean isUseBigDecimal() {
    return USE_BIG_DECIMAL.get();
  }

  private static Number parseNumberFastWithPos(char[] chars, int pos, int[] endPos) {
    Number result = parseNumberFast(chars, pos, endPos);
    return result;
  }

  private static Number parseNumberFast(char[] chars, int pos) {
    return parseNumberFast(chars, pos, new int[1]);
  }

  /**
   * 快速解析数字（内联优化，可返回解析终点）
   *
   * <p>溢出处理策略：
   *
   * <ul>
   *   <li>整数部分超过 long 范围时标记 overflow，不再累加
   *   <li>溢出后统一回退到字符串解析（BigDecimal/Double）
   *   <li>特例：-9223372036854775808（Long.MIN_VALUE）虽超出 long 正数范围， 但其负值可表示，直接返回 Long.MIN_VALUE
   * </ul>
   */
  private static Number parseNumberFast(char[] chars, int pos, int[] endPos) {
    int len = chars.length;
    int startPos = pos;

    boolean negative = false;
    if (pos < len && chars[pos] == '-') {
      negative = true;
      pos++;
    }

    long intValue = 0;
    boolean overflow = false;
    while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
      int digit = chars[pos] - '0';
      if (!overflow && intValue > (Long.MAX_VALUE - digit) / 10) {
        // 检测 long 溢出（19+ 位整数），标记后停止累加
        overflow = true;
      }
      if (!overflow) {
        intValue = intValue * 10 + digit;
      }
      pos++;
    }

    long decimalValue = 0;
    int decimalDigits = 0;
    if (pos < len && chars[pos] == '.') {
      pos++;
      while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
        int digit = chars[pos] - '0';
        if (decimalValue <= (Long.MAX_VALUE - digit) / 10) {
          decimalValue = decimalValue * 10 + digit;
        }
        decimalDigits++;
        pos++;
      }
    }

    int exp = 0;
    boolean expNegative = false;
    if (pos < len && (chars[pos] == 'e' || chars[pos] == 'E')) {
      pos++;
      if (pos < len && chars[pos] == '-') {
        expNegative = true;
        pos++;
      } else if (pos < len && chars[pos] == '+') {
        pos++;
      }
      while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
        exp = exp * 10 + (chars[pos] - '0');
        if (exp < 0) {
          throw new JsonDeserializationException("Exponent too large at position " + pos, pos);
        }
        pos++;
      }
    }

    // 溢出路径：统一使用字符串解析，避免 intValue 不准确
    if (overflow) {
      endPos[0] = pos;
      String numStr = new String(chars, startPos, pos - startPos);
      // 特例：Long.MIN_VALUE 的绝对值超出 long 正数范围，但负值可表示
      if (negative && numStr.equals("-9223372036854775808")) {
        return Long.MIN_VALUE;
      }
      if (USE_BIG_DECIMAL.get()) {
        return new BigDecimal(numStr);
      }
      return Double.parseDouble(numStr);
    }

    if (decimalDigits > 0 || exp != 0) {
      // BigDecimal 路径：金融场景精度保护
      if (USE_BIG_DECIMAL.get()) {
        BigDecimal bd = BigDecimal.valueOf(intValue);
        if (decimalDigits > 0) {
          BigDecimal decimal = BigDecimal.valueOf(decimalValue).movePointLeft(decimalDigits);
          bd = bd.add(decimal);
        }
        if (negative) {
          bd = bd.negate();
        }
        if (exp != 0) {
          int scale = expNegative ? exp : -exp;
          bd = bd.scaleByPowerOfTen(scale);
        }
        endPos[0] = pos;
        return bd;
      }
      // 精度保护：intValue 超过 2^53 或 decimalDigits 超过 22 位时
      // double 无法精确表示，回退到 Double.parseDouble 避免精度丢失
      if (intValue > 9007199254740992L || decimalDigits > 22) {
        String numStr = new String(chars, startPos, pos - startPos);
        endPos[0] = pos;
        return Double.parseDouble(numStr);
      }
      double value = (double) intValue;
      if (decimalDigits > 0) {
        value += (double) decimalValue / POW10[decimalDigits];
      }
      if (negative) {
        value = -value;
      }
      if (exp != 0) {
        if (exp < POW10.length) {
          value = expNegative ? value / POW10[exp] : value * POW10[exp];
        } else {
          // 指数超出查表范围，回退字符串解析，避免 POW10 数组越界并保证正确性
          String numStr = new String(chars, startPos, pos - startPos);
          return Double.parseDouble(numStr);
        }
      }
      endPos[0] = pos;
      return Double.valueOf(value);
    } else {
      endPos[0] = pos;
      // 行业惯例（Jackson/Fastjson2）：int 范围内返回 Integer，超出返回 Long
      long result = negative ? -intValue : intValue;
      if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
        return (int) result;
      }
      return result;
    }
  }

  private static Object parseObjectRecursiveWithPos(
      char[] chars, int start, int[] endPos, int depth) {
    Object result =
        parseObjectRecursiveImpl(chars, start, PARSE_OBJECT_INITIAL_CAPACITY, depth + 1);
    endPos[0] = getValueEndPosition(chars, start); // 对象/数组仍需一次扫描定位 `}`
    return result;
  }

  private static Object parseArrayRecursive(char[] chars, int start) {
    return parseArrayRecursiveImpl(chars, start, 1);
  }

  private static Object parseArrayRecursiveWithPos(
      char[] chars, int start, int[] endPos, int depth) {
    Object result = parseArrayRecursiveImpl(chars, start, depth + 1);
    endPos[0] = getValueEndPosition(chars, start);
    return result;
  }

  private static Object parseObjectRecursiveImpl(
      char[] chars, int start, int initialCapacity, int depth) {
    if (depth > resolveMaxParseDepth()) {
      throw new JsonDeserializationException("JSON nesting depth exceeds limit: " + depth, start);
    }
    int len = chars.length;
    Map<String, Object> result = new LinkedHashMap<>(initialCapacity);
    int pos = start + 1;

    while (pos < len) {
      // 跳过空白字符
      while (pos < len && chars[pos] <= ' ') {
        pos++;
      }

      if (pos >= len) {
        break;
      }

      // 检查是否结束
      if (chars[pos] == '}') {
        break;
      }

      // 跳过逗号
      if (chars[pos] == ',') {
        pos++;
        continue;
      }

      // 解析字段名
      if (chars[pos] != '"') {
        throw new JsonDeserializationException("Expected '\"' at position " + pos, pos);
      }
      pos++; // 跳过起始引号

      int fieldStart = pos;
      while (pos < len && chars[pos] != '"') {
        if (chars[pos] == '\\') {
          pos++; // 跳过转义字符
        }
        pos++;
      }

      String fieldName = decodeStringIfNeeded(chars, fieldStart, pos - fieldStart);
      pos++; // 跳过结束引号

      // 跳过冒号前的空白
      while (pos < len && chars[pos] <= ' ') {
        pos++;
      }

      if (pos >= len || chars[pos] != ':') {
        throw new JsonDeserializationException("Expected ':' at position " + pos, pos);
      }
      pos++; // 跳过冒号

      // 跳过值前的空白
      while (pos < len && chars[pos] <= ' ') {
        pos++;
      }

      // 解析值（返回解析终点，消除 getValueEndFast 二次扫描）
      int[] endPos = new int[1];
      Object value = parseValueWithPos(chars, pos, endPos, depth + 1);
      result.put(fieldName, value);
      pos = endPos[0];
    }

    return result;
  }

  private static int estimateArraySize(char[] chars, int start) {
    int count = 0;
    int len = chars.length;
    boolean inString = false;
    for (int i = start; i < len && chars[i] != ']'; i++) {
      char c = chars[i];
      if (c == '"') {
        inString = !inString;
      } else if (!inString && c == ',') {
        count++;
      }
    }
    return count + 1;
  }

  /** 递归解析数组（从 char 数组的指定位置开始） */
  private static Object parseArrayRecursiveImpl(char[] chars, int start, int depth) {
    if (depth > resolveMaxParseDepth()) {
      throw new JsonDeserializationException("JSON nesting depth exceeds limit: " + depth, start);
    }
    int len = chars.length;
    int estimatedSize = estimateArraySize(chars, start + 1);
    List<Object> result = new ArrayList<>(Math.max(estimatedSize, 4));
    int pos = start + 1;

    while (pos < len) {
      // 跳过空白
      while (pos < len && chars[pos] <= ' ') {
        pos++;
      }

      if (pos >= len) {
        break;
      }

      // 检查是否结束
      if (chars[pos] == ']') {
        break;
      }

      // 跳过逗号
      if (chars[pos] == ',') {
        pos++;
        continue;
      }

      // 解析值（返回解析终点，消除 getValueEndFast 二次扫描）
      int[] endPos = new int[1];
      Object value = parseValueWithPos(chars, pos, endPos, depth + 1);
      result.add(value);
      pos = endPos[0];
    }

    return result;
  }

  /**
   * 检查字符数组片段是否包含转义字符，若包含则解码转义序列，否则直接返回子串。
   *
   * <p>快速路径：先扫描是否有 '\' 字符，若无则直接 new String(chars, start, len)。
   *
   * <p>慢速路径：复用 parseStringWithEscape 逻辑解码转义序列。
   *
   * @param chars 字符数组
   * @param start 起始位置
   * @param length 长度
   * @return 解码后的字符串
   */
  private static String decodeStringIfNeeded(char[] chars, int start, int length) {
    for (int i = start; i < start + length; i++) {
      if (chars[i] == '\\') {
        return parseStringWithEscape(chars, start, start + length);
      }
    }
    return new String(chars, start, length);
  }

  /** 解析字符串 */
  /** package-private */
  static String parseString(char[] chars, int pos) {
    int len = chars.length;

    if (chars[pos] != '"') {
      throw new JsonDeserializationException("Expected '\"' at position " + pos, pos);
    }
    pos++; // 跳过起始引号

    StringBuilder sb = new StringBuilder(len - pos);

    while (pos < len) {
      char c = chars[pos];
      if (c == '"') {
        // 结束引号
        return sb.toString();
      } else if (c == '\\') {
        // 转义字符
        pos++;
        if (pos >= len) {
          throw new JsonDeserializationException("Unexpected end of string", pos);
        }
        char escaped = chars[pos];
        switch (escaped) {
          case '"':
            sb.append('"');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '/':
            sb.append('/');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'u':
            // Unicode 转义
            if (pos + 4 >= len) {
              throw new JsonDeserializationException(
                  "Invalid unicode escape at position " + pos, pos);
            }
            String hex = new String(chars, pos + 1, 4);
            sb.append((char) Integer.parseInt(hex, 16));
            pos += 4;
            break;
          default:
            sb.append(escaped);
        }
      } else {
        sb.append(c);
      }
      pos++;
    }

    throw new JsonDeserializationException("Unterminated string", len - 1);
  }

  /** 解析数字 */
  /** package-private */
  static Number parseNumber(char[] chars, int pos) {
    int len = chars.length;
    int start = pos;

    // 跳过负号
    if (pos < len && chars[pos] == '-') {
      pos++;
    }

    // 解析整数部分
    while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
      pos++;
    }

    // 检查小数
    boolean isDecimal = false;
    if (pos < len && chars[pos] == '.') {
      isDecimal = true;
      pos++;
      while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
        pos++;
      }
    }

    // 检查指数
    if (pos < len && (chars[pos] == 'e' || chars[pos] == 'E')) {
      isDecimal = true;
      pos++;
      if (pos < len && (chars[pos] == '+' || chars[pos] == '-')) {
        pos++;
      }
      while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
        pos++;
      }
    }

    String numStr = new String(chars, start, pos - start);
    if (isDecimal) {
      return Double.parseDouble(numStr);
    } else {
      try {
        return Long.parseLong(numStr);
      } catch (NumberFormatException e) {
        return Double.parseDouble(numStr);
      }
    }
  }

  /**
   * 获取值的结束位置（快速版，消除简单值的二次扫描）
   *
   * <p>对于 true/false/null 值，直接根据值类型计算结束位置， 无需调用 getValueEndPosition 重新扫描。
   *
   * @param chars JSON 字符数组
   * @param valueStart 值的起始位置
   * @param value 已解析的值
   * @param len 字符数组长度
   * @return 值的结束位置
   */
  private static int getValueEndFast(char[] chars, int valueStart, Object value, int len) {
    // 快速路径：布尔值和 null 直接计算长度
    if (value == Boolean.TRUE) {
      return valueStart + 4; // "true"
    }
    if (value == Boolean.FALSE) {
      return valueStart + 5; // "false"
    }
    if (value == null && valueStart + 4 <= len && chars[valueStart] == 'n') {
      return valueStart + 4; // "null"
    }
    // 复杂值（String/Number/Map/List）：需要扫描确定结束位置
    return getValueEndPosition(chars, valueStart);
  }

  /** 获取值的结束位置 */
  private static int getValueEndPosition(char[] chars, int pos) {
    int len = chars.length;

    // 跳过空白
    while (pos < len && chars[pos] <= ' ') {
      pos++;
    }

    if (pos >= len) {
      return pos;
    }

    char c = chars[pos];

    if (c == '"') {
      // 字符串：找到结束引号
      pos++;
      while (pos < len) {
        if (chars[pos] == '\\' && pos + 1 < len) {
          pos += 2; // 跳过转义字符
        } else if (chars[pos] == '"') {
          return pos + 1;
        } else {
          pos++;
        }
      }
    } else if (c == '{') {
      // 对象：找到匹配的 }
      return findEndPosition(chars, pos, '{', '}') + 1;
    } else if (c == '[') {
      // 数组：找到匹配的 ]
      return findEndPosition(chars, pos, '[', ']') + 1;
    } else {
      // 基本类型：找到逗号、} 或 ]
      while (pos < len) {
        char ch = chars[pos];
        if (ch == ',' || ch == '}' || ch == ']') {
          return pos;
        }
        pos++;
      }
    }

    return pos;
  }

  /** 查找匹配的结束位置 */
  private static int findEndPosition(char[] chars, int start, char openChar, char closeChar) {
    int depth = 0;
    int pos = start;
    int len = chars.length;

    while (pos < len) {
      char c = chars[pos];
      if (c == openChar) {
        depth++;
      } else if (c == closeChar) {
        depth--;
        if (depth == 0) {
          return pos;
        }
      } else if (c == '"') {
        // 跳过字符串
        pos++;
        while (pos < len && chars[pos] != '"') {
          if (chars[pos] == '\\' && pos + 1 < len) {
            pos += 2;
          } else {
            pos++;
          }
        }
      }
      pos++;
    }

    throw new JsonDeserializationException("Unmatched bracket: " + openChar, pos);
  }

  /** 获取字符数组缓冲区（JIT 优化：final 方法） */
  private static char[] getCharBuffer(String json) {
    char[] buffer = CHAR_BUFFER.get();
    if (buffer.length < json.length()) {
      buffer = new char[json.length()];
      CHAR_BUFFER.set(buffer);
    }
    json.getChars(0, json.length(), buffer, 0);
    return buffer;
  }

  /** 快速跳过空白字符（向量化优化） */
  private static int skipWhitespace(char[] chars, int pos) {
    int len = chars.length;

    // 向量化处理：一次检查 8 个字符
    while (pos + 7 < len) {
      boolean allWhitespace = true;
      for (int i = 0; i < 8; i++) {
        if (chars[pos + i] > ' ') {
          allWhitespace = false;
          pos += i;
          break;
        }
      }
      if (!allWhitespace) {
        break;
      }
      pos += 8;
    }

    // 处理剩余字符
    while (pos < len && chars[pos] <= ' ') {
      pos++;
    }

    return pos;
  }

  // ==================== 字段级快速解析方法（解析器内部专用） ====================

  /**
   * 单遍扫描构建字段位置映射（优化 O(N*M) 为 O(N)）。
   *
   * <p>当解析器需要解析多个字段时，传统方式对每个字段调用 {@link #findFieldPosition} 导致 O(N*M) 复杂度。此方法单遍扫描 JSON，
   * 一次性提取所有顶层字段名及其值起始位置，将复杂度降为 O(N+M)。
   *
   * @param json JSON 字符串
   * @return 字段名 -> 值起始位置（冒号后第一个非空白字符）的映射
   * @since 26.09.01
   */
  public static Map<String, Integer> buildFieldPositionMap(String json) {
    Map<String, Integer> fieldPositions = new HashMap<>(16);
    int len = json.length();
    int i = 0;
    // 跳过前导空白
    while (i < len && json.charAt(i) <= ' ') {
      i++;
    }
    if (i >= len || json.charAt(i) != '{') {
      return fieldPositions;
    }
    i++; // 跳过 '{'

    while (i < len) {
      // 跳过空白
      while (i < len && json.charAt(i) <= ' ') {
        i++;
      }
      if (i >= len) {
        break;
      }
      if (json.charAt(i) == '}') {
        break;
      }
      if (json.charAt(i) == ',') {
        i++;
        continue;
      }

      // 读取字段名（带引号）
      if (json.charAt(i) != '"') {
        break;
      }
      i++; // 跳过起始引号
      int nameStart = i;
      while (i < len && json.charAt(i) != '"') {
        if (json.charAt(i) == '\\') {
          i++;
        }
        i++;
      }
      String fieldName = json.substring(nameStart, i);
      i++; // 跳过结束引号

      // 跳过冒号和空白
      while (i < len && json.charAt(i) != ':') {
        i++;
      }
      i++; // 跳过冒号
      while (i < len && json.charAt(i) <= ' ') {
        i++;
      }

      // 记录值起始位置
      fieldPositions.put(fieldName, i);

      // 跳过值（根据类型）
      i = skipValue(json, i);
    }
    return fieldPositions;
  }

  /** 跳过 JSON 值，返回值结束后的下一个位置。 */
  private static int skipValue(String json, int start) {
    int len = json.length();
    if (start >= len) {
      return start;
    }
    char c = json.charAt(start);
    if (c == '"') {
      // 字符串值
      int i = start + 1;
      while (i < len) {
        if (json.charAt(i) == '\\') {
          i += 2;
          continue;
        }
        if (json.charAt(i) == '"') {
          return i + 1;
        }
        i++;
      }
      return i;
    } else if (c == '{' || c == '[') {
      // 嵌套对象/数组：计算深度
      int depth = 0;
      boolean inString = false;
      boolean escaped = false;
      for (int i = start; i < len; i++) {
        char ch = json.charAt(i);
        if (inString) {
          if (escaped) {
            escaped = false;
          } else if (ch == '\\') {
            escaped = true;
          } else if (ch == '"') {
            inString = false;
          }
        } else {
          if (ch == '"') {
            inString = true;
          } else if (ch == '{' || ch == '[') {
            depth++;
          } else if (ch == '}' || ch == ']') {
            depth--;
            if (depth == 0) {
              return i + 1;
            }
          }
        }
      }
      return len;
    } else {
      // 基本类型（number/boolean/null）
      int i = start;
      while (i < len) {
        char ch = json.charAt(i);
        if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ') {
          return i;
        }
        i++;
      }
      return i;
    }
  }

  /**
   * 在 JSON 中查找字段名的位置（跳过字符串值内部的文本）。
   *
   * <p>使用此方法替代 {@code json.indexOf(fieldJson)}， 避免 JSON 字符串值中包含类似字段名格式的文本时误匹配。
   *
   * @param json JSON 字符串
   * @param fieldJson 字段 JSON 片段（如 {@code "name":}）
   * @return 字段位置，未找到返回 -1
   */
  static int findFieldPosition(String json, String fieldJson) {
    int len = json.length();
    int fieldLen = fieldJson.length();
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i <= len - fieldLen; i++) {
      char c = json.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
      } else {
        // 先检查是否匹配字段模式（fieldJson 以 '"' 开头），
        // 再判断是否进入字符串值
        if (c == fieldJson.charAt(0) && json.regionMatches(i, fieldJson, 0, fieldLen)) {
          return i;
        } else if (c == '"') {
          inString = true;
        }
      }
    }
    return -1;
  }

  /**
   * 解析 int 字段（解析器直接调用）
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段对应的 int 值；字段不存在或字段值无法解析为整数时返回 {@code 0}（不抛异常）
   */
  public static int parseIntField(String json, String fieldName) {
    String fieldJson = "\"" + fieldName + "\":";
    int fieldPos = findFieldPosition(json, fieldJson);
    if (fieldPos == -1) {
      return 0;
    }

    int valueStart = fieldPos + fieldJson.length();
    while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
      valueStart++;
    }

    int valueEnd = valueStart;
    while (valueEnd < json.length()
        && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
      valueEnd++;
    }

    try {
      return Integer.parseInt(json.substring(valueStart, valueEnd));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 解析 long 字段（解析器直接调用）
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段对应的 long 值；字段不存在或字段值无法解析为长整数时返回 {@code 0L}（不抛异常）
   */
  public static long parseLongField(String json, String fieldName) {
    String fieldJson = "\"" + fieldName + "\":";
    int fieldPos = findFieldPosition(json, fieldJson);
    if (fieldPos == -1) {
      return 0L;
    }

    int valueStart = fieldPos + fieldJson.length();
    while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
      valueStart++;
    }

    int valueEnd = valueStart;
    while (valueEnd < json.length()
        && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
      valueEnd++;
    }

    try {
      return Long.parseLong(json.substring(valueStart, valueEnd));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  /**
   * 解析 double 字段（解析器直接调用）
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段对应的 double 值；字段不存在或字段值无法解析为浮点数时返回 {@code 0.0}（不抛异常）
   */
  public static double parseDoubleField(String json, String fieldName) {
    String fieldJson = "\"" + fieldName + "\":";
    int fieldPos = findFieldPosition(json, fieldJson);
    if (fieldPos == -1) {
      return 0.0;
    }

    int valueStart = fieldPos + fieldJson.length();
    while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
      valueStart++;
    }

    int valueEnd = valueStart;
    while (valueEnd < json.length()
        && (Character.isDigit(json.charAt(valueEnd))
            || json.charAt(valueEnd) == '-'
            || json.charAt(valueEnd) == '.')) {
      valueEnd++;
    }

    try {
      return Double.parseDouble(json.substring(valueStart, valueEnd));
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  /**
   * 解析 String 字段（解析器直接调用）
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段对应的字符串，含转义序列时已解码；
   *     字段不存在或字段值不是 JSON 字符串时返回 {@code null}
   */
  public static String parseStringField(String json, String fieldName) {
    String fieldJson = "\"" + fieldName + "\":";
    int fieldPos = findFieldPosition(json, fieldJson);
    if (fieldPos == -1) {
      return null;
    }

    int valueStart = fieldPos + fieldJson.length();
    while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
      valueStart++;
    }

    if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
      return null;
    }
    valueStart++; // 跳过起始引号

    int valueEnd = valueStart;
    boolean hasEscape = false;
    while (valueEnd < json.length() && json.charAt(valueEnd) != '"') {
      if (json.charAt(valueEnd) == '\\') {
        valueEnd++; // 跳过转义字符
        hasEscape = true;
      }
      valueEnd++;
    }

    // 如果包含转义字符，需要解码后再返回
    if (hasEscape) {
      char[] chars = json.toCharArray();
      return parseStringWithEscape(chars, valueStart, valueEnd);
    }
    return json.substring(valueStart, valueEnd);
  }

  /**
   * 解析 boolean 字段（解析器直接调用）
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段值为 JSON {@code true} 时返回 {@code true}；
   *     字段不存在、值为 {@code false} 或其他非 {@code true} 内容时均返回 {@code false}
   */
  public static boolean parseBooleanField(String json, String fieldName) {
    String fieldJson = "\"" + fieldName + "\":";
    int fieldPos = findFieldPosition(json, fieldJson);
    if (fieldPos == -1) {
      return false;
    }

    int valueStart = fieldPos + fieldJson.length();
    while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
      valueStart++;
    }

    if (json.startsWith("true", valueStart)) {
      return true;
    } else if (json.startsWith("false", valueStart)) {
      return false;
    }
    return false;
  }

  /**
   * 解析指定字段的值（解析器直接调用）。
   *
   * <p>从 JSON 中查找指定字段名的值并解析为 Object。
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段值，字段不存在时返回 null
   */
  public static Object parseObjectField(String json, String fieldName) {
    String fieldJson = "\"" + fieldName + "\":";
    int fieldPos = findFieldPosition(json, fieldJson);
    if (fieldPos == -1) {
      return null;
    }

    int valueStart = fieldPos + fieldJson.length();
    while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
      valueStart++;
    }

    if (valueStart >= json.length()) {
      return null;
    }

    char c = json.charAt(valueStart);
    if (c == '"') {
      return parseStringField(json, fieldName);
    } else if (c == '{') {
      int end = findEndPosition(json.toCharArray(), valueStart, '{', '}') + 1;
      return parseObject(json.substring(valueStart, end));
    } else if (c == '[') {
      int end = findEndPosition(json.toCharArray(), valueStart, '[', ']') + 1;
      return parseArray(json.substring(valueStart, end));
    } else if (json.startsWith("true", valueStart)) {
      return Boolean.TRUE;
    } else if (json.startsWith("false", valueStart)) {
      return Boolean.FALSE;
    } else if (json.startsWith("null", valueStart)) {
      return null;
    } else {
      // 数值
      int valueEnd = valueStart;
      while (valueEnd < json.length()
          && json.charAt(valueEnd) != ','
          && json.charAt(valueEnd) != '}'
          && json.charAt(valueEnd) != ']'
          && json.charAt(valueEnd) > ' ') {
        valueEnd++;
      }
      String numStr = json.substring(valueStart, valueEnd);
      try {
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
          return Double.parseDouble(numStr);
        }
        return Long.parseLong(numStr);
      } catch (NumberFormatException e) {
        return numStr;
      }
    }
  }

  /**
   * 解析 JSON 数组（带类型参数，用于降级）
   *
   * @param <T> 泛型类型
   * @param json JSON 字符串
   * @param clazz 目标类型
   * @return 元素已按 {@code clazz} 逐个强转的列表；底层解析返回 {@code null} 时本方法同样返回 {@code null}，
   *     数组为空时返回空列表。元素实际类型与 {@code clazz} 不符时抛出 {@link ClassCastException}
   */
  public static <T> List<T> parseArray(String json, Class<T> clazz) {
    List<Object> list = parseArray(json);
    if (list == null) {
      return null;
    }
    List<T> typedList = new ArrayList<>(list.size());
    for (Object item : list) {
      typedList.add(clazz.cast(item));
    }
    return typedList;
  }

  /**
   * 解析 JSON 对象（带类型参数，用于降级）
   *
   * @param <T> 泛型类型
   * @param json JSON 字符串
   * @param clazz 目标类型
   * @return 反序列化后的目标类型实例；{@code clazz} 为 {@code Map} 及其子类型时直接返回解析出的 {@code Map}，
   *     否则先解析为 {@code Map} 再经 JSON 往返转换为目标 Bean。JSON 为空时返回 {@code null}
   */
  public static <T> T parseObject(String json, Class<T> clazz) {
    // Map 及其子类直接 cast 返回
    if (Map.class.isAssignableFrom(clazz)) {
      Map<String, Object> map = parseObject(json);
      if (map == null) {
        return null;
      }
      return clazz.cast(map);
    }
    // 非 Map 类型：解析为 Map 后委托 YdszJson 反序列化为目标 Bean
    Map<String, Object> map = parseObject(json);
    if (map == null) {
      return null;
    }
    return YdszJson.fromJson(YdszJson.toJson(map), clazz);
  }
}
