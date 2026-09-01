package com.njydsz.common.json.provider;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.temporal.Temporal;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonSerialize;
import com.njydsz.common.json.cache.BeanSerializerCache;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.exception.JsonSerializationException;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.internal.JsonRuntimeConfig;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.serializer.SerializerRegistry;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.util.BoundedLruCache;
import com.njydsz.common.json.writer.BeanSerializer;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * YdszJson 序列化提供者（架构层）
 *
 * <p>架构层级：YdszJson => Engine => Provider => Parser
 *
 * <p><b>ThreadLocal 清理机制：</b>
 *
 * <ul>
 *   <li>序列化完成后自动清理循环引用检测集。
 *   <li>使用 try-finally 确保异常时也清理
 * </ul>
 *
 * <p><b>FastJSON2 深度优化技术：</b>
 *
 * <ul>
 *   <li>精确容量预分配 - 基于对象结构预估 JSON 大小，避免 StringBuilder 扩容
 *   <li>快速数字编码 - 直接写入字符数组，避免方法调用和边界检查
 *   <li>UTF-8 编码优化 - 针对 ASCII 字符集优化
 *   <li>热路径内联 - 减少虚方法调用和方法调用开销
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
public final class SerializationProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(SerializationProvider.class);

  /** StringBuilder 池最大容量 */
  private static final int MAX_SB_CAPACITY = 65536;

  /** 小 JSON StringBuilder 初始容量（适合简单 Bean） */
  private static final int SMALL_SB_CAPACITY = 1024;

  /** 中 JSON StringBuilder 初始容量（适合一般 Bean） */
  private static final int MEDIUM_SB_CAPACITY = 4096;

  /** 大 JSON StringBuilder 初始容量（适合大集合/复杂嵌套） */
  private static final int LARGE_SB_CAPACITY = 16384;

  /**
   * 序列化上下文（对标 Jackson SerializerProvider）。
   *
   * <p>合并 10 个原独立 ThreadLocal 字段到单一对象，降低 ThreadLocal 粒度， 方便快照保存/恢复。通过 {@link #CONTEXT} ThreadLocal
   * 访问。
   *
   * <p><b>namingStrategy 说明：</b>命名策略未并入此上下文（仍由 {@link FieldMetadataLoader#NAMING_STRATEGY}
   * 持有），因其需通过 {@link BeanSerializerCache} 的二级缓存 Key（Class + NamingStrategy）直接访问， 与序列化流程解耦。快照 {@link
   * ThreadLocalSnapshot} 单独保存/恢复它。
   *
   * @since 1.0.0
   */
  public static final class SerializationContext {

    /** 线程级 Context 持有者（自动初始化默认实例） */
    // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
    public static final ThreadLocal<SerializationContext> CONTEXT =
        ThreadLocal.withInitial(
            () -> {
              SerializationContext ctx = new SerializationContext();
              ctx.sbPool = new StringBuilder(SMALL_SB_CAPACITY);
              ctx.serializingObjects = Collections.newSetFromMap(new IdentityHashMap<>());
              ctx.fastWriterPool = new JSONWriter();
              return ctx;
            });
    // CHECKSTYLE.ON: RegexpSinglelineJava

    /** 是否输出 null 值字段 */
    public boolean writeNulls;

    /** 是否格式化输出 */
    public boolean prettyPrint;

    /** 循环引用处理策略（REF/IGNORE/ERROR） */
    public String circularRefStrategy;

    /** 枚举是否使用序号 */
    public boolean serializeEnumUsingOrdinal;

    /** 排除字段集合 */
    public Set<String> excludedFields;

    /** 日期格式 */
    public String dateFormat;

    /** 序列化失败时是否抛出异常 */
    public boolean failOnError;

    /** JSONWriter 实例池 */
    public JSONWriter fastWriterPool;

    /** 当前 JsonView 视图类 */
    public Class<?> currentViewClass;

    /** 当前正在序列化的对象集合（循环引用检测） */
    public Set<Object> serializingObjects;

    /** StringBuilder 实例池 */
    public StringBuilder sbPool;

    /** 字段命名策略（PropertyNamingStrategy） */
    public PropertyNamingStrategy namingStrategy;

    /** 当前序列化深度（防止 StackOverflow 的安全网） */
    public int serializationDepth;

    /**
     * 池化缓冲重入防护深度（P0 修复）。
     *
     * <p>序列化入口方法（serialize / serializeToBytes / format / serializeWithView 等）
     * 进入时 +1、退出时 -1。仅最外层（depth ≤ 1）允许复用 fastWriterPool / sbPool；
     * 嵌套序列化（容器内 Bean 经 JSONWriter.writeObjectInline → YdszJson.toJson 重入）
     * 必须使用独立缓冲实例，避免嵌套对池实例 reset() 时清空外层已写入内容、
     * 或对 sbPool setLength(0) 时污染外层缓冲。
     */
    int poolDepth;

    private SerializationContext() {
      // 仅内部创建
    }

    /** 清除当前线程的 SerializationContext。 */
    public static void clear() {
      CONTEXT.remove();
    }

    /**
     * 从预计算运行时配置创建并初始化 SerializationContext。
     *
     * @param runtimeConfig 运行时配置
     * @return 初始化后的 SerializationContext
     */
    public static SerializationContext from(JsonRuntimeConfig runtimeConfig) {
      SerializationContext ctx = new SerializationContext();
      ctx.writeNulls = runtimeConfig.writeNulls();
      ctx.prettyPrint = runtimeConfig.prettyPrint();
      ctx.circularRefStrategy = runtimeConfig.circularRefStrategy();
      ctx.serializeEnumUsingOrdinal = runtimeConfig.serializeEnumUsingOrdinal();
      ctx.dateFormat = runtimeConfig.dateFormat();
      ctx.failOnError = runtimeConfig.failOnError();
      ctx.fastWriterPool = new JSONWriter();
      ctx.currentViewClass = null;
      ctx.serializingObjects = Collections.newSetFromMap(new IdentityHashMap<>());
      ctx.sbPool = new StringBuilder(SMALL_SB_CAPACITY);
      ctx.namingStrategy = runtimeConfig.namingStrategy();
      return ctx;
    }

    /**
     * 获取当前线程的 SerializationContext（已由 ThreadLocal.withInitial 保证非空）。
     *
     * @return 当前线程的 SerializationContext
     */
    public static SerializationContext current() {
      return CONTEXT.get();
    }
  }

  /**
   * 所有序列化上下文状态（含 StringBuilder 池、JSONWriter 池、循环引用检测集、 视图类、排除字段集合、writeNulls、prettyPrint、
   * circularRefStrategy、serializeEnumUsingOrdinal 等 11 个原 ThreadLocal 字段） 已合并到 {@link
   * SerializationContext#CONTEXT} 单一 ThreadLocal 中。
   */

  /**
   * 序列化字段路径栈（ThreadLocal）。
   *
   * <p>序列化 Bean 中嵌套对象时，栈中保存从根对象到当前对象经过的字段名。 序列化异常抛出时，可读取此栈拼出 {@code user.address.street} 样式的路径，
   * 大幅降低排障成本。对标 Jackson {@code JsonMappingException.getPath()}。
   */
  private static final ThreadLocal<Deque<String>> FIELD_PATH_STACK =
      ThreadLocal.withInitial(ArrayDeque::new);

  /**
   * 压入字段名（序列化进入嵌套对象前调用）。
   *
   * @param fieldName 字段名（Java 字段名，非 JSON key）
   */
  public static void pushFieldPath(String fieldName) {
    if (fieldName != null && !fieldName.isEmpty()) {
      FIELD_PATH_STACK.get().addLast(fieldName);
    }
  }

  /** 弹出字段名（序列化退出嵌套对象后调用）。 */
  public static void popFieldPath() {
    Deque<String> stack = FIELD_PATH_STACK.get();
    if (!stack.isEmpty()) {
      stack.pollLast();
    }
  }

  /**
   * 读取当前字段路径（序列化异常时调用）。
   *
   * @return 点分路径字符串，无路径时返回空字符串
   */
  public static String getCurrentFieldPath() {
    Deque<String> stack = FIELD_PATH_STACK.get();
    if (stack.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(128);
    boolean first = true;
    for (String segment : stack) {
      if (!first) {
        sb.append('.');
      }
      sb.append(segment);
      first = false;
    }
    return sb.toString();
  }

  /**
   * Bean 序列化信息双层缓存（Class -> 命名策略 -> BeanSerializerInfo）， 外层使用有界 LRU 缓存（容量 1024），防止热部署/动态类加载场景下无限增长。
   */
  private static final BoundedLruCache<
          Class<?>, ConcurrentMap<PropertyNamingStrategy, BeanSerializerInfo>>
      BEAN_SERIALIZER_INFO_CACHE = new BoundedLruCache<>(1024);

  private SerializationProvider() {
    throw new UnsupportedOperationException();
  }

  /**
   * 设置当前线程的字段命名策略（PropertyNamingStrategy）。
   *
   * <p>命名策略存放于 {@link FieldMetadataLoader#NAMING_STRATEGY} 这个<b>独立</b> ThreadLocal 中， 不在 {@code
   * SerializationContext} 合并的 11 个字段范围内。因此 {@link #clearThreadLocals()} 会单独清理它，且 {@link
   * ThreadLocalSnapshot} 也会保存/恢复它，避免跨调用配置泄漏。
   *
   * <p>命名策略仅影响序列化时的字段名映射（如驼峰转下划线），需在序列化前设置； 请勿在单次序列化中途变更，否则可能产生字段名不一致的 JSON 输出。
   *
   * @param strategy 命名策略实例，null 表示回退到默认（驼峰不变）策略
   */
  public static void setNamingStrategy(PropertyNamingStrategy strategy) {
    FieldMetadataLoader.NAMING_STRATEGY.set(strategy);
  }

  /**
   * 获取当前线程的命名策略。
   *
   * @return 当前命名策略；未设置时返回 {@code null}（调用方按驼峰不变处理）
   */
  public static PropertyNamingStrategy getNamingStrategy() {
    return FieldMetadataLoader.NAMING_STRATEGY.get();
  }

  /**
   * 设置当前线程是否序列化 null 值字段。
   *
   * @param writeNulls {@code true} 表示输出 null 字段，{@code false} 表示忽略
   */
  public static void setWriteNulls(boolean writeNulls) {
    SerializationContext.CONTEXT.get().writeNulls = writeNulls;
  }

  /**
   * 查询当前线程是否序列化 null 值字段。
   *
   * @return {@code true} 表示输出 null 字段
   */
  public static boolean isWriteNulls() {
    return SerializationContext.CONTEXT.get().writeNulls;
  }

  /**
   * 设置当前线程是否启用美化输出（pretty print）。
   *
   * @param prettyPrint {@code true} 表示输出带缩进的格式化 JSON
   */
  public static void setPrettyPrint(boolean prettyPrint) {
    SerializationContext.CONTEXT.get().prettyPrint = prettyPrint;
  }

  /**
   * 查询当前线程是否启用美化输出。
   *
   * @return {@code true} 表示输出带缩进的格式化 JSON
   */
  public static boolean isPrettyPrint() {
    return SerializationContext.CONTEXT.get().prettyPrint;
  }

  /**
   * 设置当前线程的循环引用处理策略。
   *
   * @param strategyName 策略名称（如 {@code REF}、{@code NULL}、{@code THROW}）， 需与 {@link
   *     com.njydsz.common.json.internal.JsonConfig.CircularReferenceStrategy} 枚举名一致
   */
  public static void setCircularReferenceStrategy(String strategyName) {
    SerializationContext.CONTEXT.get().circularRefStrategy = strategyName;
  }

  /**
   * 获取当前线程的循环引用处理策略。
   *
   * @return 策略名称字符串；未设置时返回 {@code null}
   */
  public static String getCircularReferenceStrategy() {
    return SerializationContext.CONTEXT.get().circularRefStrategy;
  }

  /**
   * 设置当前线程是否以 ordinal（序号）形式序列化枚举。
   *
   * @param ordinal {@code true} 表示输出枚举序号（如 {@code 0}）， {@code false} 表示输出枚举名（如 {@code "ACTIVE"}）
   */
  public static void setSerializeEnumUsingOrdinal(boolean ordinal) {
    SerializationContext.CONTEXT.get().serializeEnumUsingOrdinal = ordinal;
  }

  /**
   * 查询当前线程是否以 ordinal 形式序列化枚举。
   *
   * @return {@code true} 表示输出枚举序号
   */
  public static boolean isSerializeEnumUsingOrdinal() {
    return SerializationContext.CONTEXT.get().serializeEnumUsingOrdinal;
  }

  /**
   * 设置当前线程的全局日期格式。
   *
   * @param dateFormat 日期格式字符串，空字符串表示使用默认 ISO 格式
   * @since 1.0.0
   */
  public static void setDateFormat(String dateFormat) {
    SerializationContext.CONTEXT.get().dateFormat = dateFormat != null ? dateFormat : "";
  }

  /**
   * 获取当前线程的全局日期格式。
   *
   * @return 日期格式字符串，空字符串表示使用默认 ISO 格式
   * @since 1.0.0
   */
  public static String getDateFormat() {
    return SerializationContext.CONTEXT.get().dateFormat;
  }

  /**
   * 设置序列化失败时是否抛出异常。
   *
   * @param failOnError true=抛异常，false=记录日志返回 null
   * @since 1.0.0
   */
  public static void setFailOnError(boolean failOnError) {
    SerializationContext.CONTEXT.get().failOnError = failOnError;
  }

  /**
   * 获取序列化失败时是否抛出异常。
   *
   * @return true=抛异常，false=记录日志返回 null
   * @since 1.0.0
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  public static boolean isFailOnError() {
  // CHECKSTYLE.ON: RegexpSinglelineJava
    return SerializationContext.CONTEXT.get().failOnError;
  }

  /**
   * 清理当前线程的 ThreadLocal 对象
   *
   * <p>在线程池环境中，应在任务完成后或线程归还前调用此方法
   *
    // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
   * <p>注：11 个原 ThreadLocal 已合并到 {@link SerializationContext#CONTEXT}， 调用 {@link
    // CHECKSTYLE.ON: RegexpSinglelineJava
   * SerializationContext#clear()} 一次即可全部清理。 {@code FieldMetadataLoader.NAMING_STRATEGY}、{@code
   * JsonParserUtil#useBigDecimal}、 {@code JSONReader.READER_POOL}、{@code
  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
   * DeserializationProvider.DESERIALIZE_DEPTH} 属于独立 ThreadLocal，需一并清理。
  // CHECKSTYLE.ON: RegexpSinglelineJava
   */
  public static void clearThreadLocals() {
    SerializationContext.clear();
    FieldMetadataLoader.NAMING_STRATEGY.remove();
    JsonParserUtil.clearThreadLocals();
    JSONReader.clearThreadLocals();
    DeserializationProvider.clearThreadLocals();
  }

  /**
   * 设置当前线程是否使用 BigDecimal 解析浮点数。
   *
   * <p>替代直接调用 {@link JsonParserUtil#setUseBigDecimal(boolean)}， 同时将其纳入 {@link ThreadLocalSnapshot}
   * 保存/恢复范围， 避免不同配置的 Mapper 在使用后相互泄漏。
   *
   * @param enabled {@code true} 表示将浮点数解析为 BigDecimal，避免精度丢失
   */
  public static void setUseBigDecimal(boolean enabled) {
    JsonParserUtil.setUseBigDecimal(enabled);
  }

  /**
   * 查询当前线程是否使用 BigDecimal 解析浮点数。
   *
   * @return {@code true} 表示使用 BigDecimal 解析
   */
  public static boolean isUseBigDecimal() {
    return JsonParserUtil.isUseBigDecimal();
  }

  /**
   * 设置需要排除的字段名集合。
   *
   * <p>在序列化时，集合中的字段名（JSON 名称）对应的字段将被跳过，不输出到 JSON 中。 适用于列权限字段过滤等场景。
   *
   * @param fieldNames 需要排除的字段名集合，null 表示清除排除
   */
  public static void setExcludedFields(Set<String> fieldNames) {
    SerializationContext.CONTEXT.get().excludedFields = fieldNames;
  }

  /**
   * 获取需要排除的字段名集合。
   *
   * @return 排除集合，null 表示不排除任何字段
   */
  public static Set<String> getExcludedFields() {
    return SerializationContext.CONTEXT.get().excludedFields;
  }

  /**
   * 判断指定字段是否被排除。
   *
   * <p>支持两种输入格式：
   *
   * <ul>
   *   <li>纯字段名：{@code fieldName}
   *   <li>JSON 键格式：{@code "fieldName":}（BeanSerializer 内部格式）
   * </ul>
   *
   * @param keyOrName 字段的 JSON 键或纯名称
   * @return true 表示该字段应被排除
   */
  public static boolean isFieldExcluded(String keyOrName) {
    Set<String> excluded = SerializationContext.CONTEXT.get().excludedFields;
    if (excluded == null || keyOrName == null) {
      return false;
    }
    // 直接匹配纯字段名
    if (excluded.contains(keyOrName)) {
      return true;
    }
    // 尝试从 JSON 键格式 "fieldName": 中提取纯名称
    if (keyOrName.length() >= 3 && keyOrName.charAt(0) == '"') {
      int end = keyOrName.indexOf('"', 1);
      if (end > 0) {
        String name = keyOrName.substring(1, end);
        return excluded.contains(name);
      }
    }
    return false;
  }

  // ==================== 向后兼容的静态访问器（原 ThreadLocal 字段的替代） ====================

  /**
   * 获取当前线程的 JSONWriter 池实例（替代原 {@code FAST_WRITER_POOL.get()}）。
   *
   * @return 当前线程的 JSONWriter
   * @since 1.0.0
   */
  public static JSONWriter getFastWriterPool() {
    // 池化缓冲重入防护：嵌套序列化返回独立实例，避免 reset() 清空外层内容（P0 修复）
    SerializationContext ctx = SerializationContext.CONTEXT.get();
    if (ctx.poolDepth > 1) {
      return new JSONWriter();
    }
    return ctx.fastWriterPool;
  }

  /**
   * 获取当前线程的视图类（替代原 {@code CURRENT_VIEW_CLASS.get()}）。
   *
   * @return 当前线程的视图类，null 表示无视图过滤
   * @since 1.0.0
   */
  public static Class<?> getCurrentViewClass() {
    return SerializationContext.CONTEXT.get().currentViewClass;
  }

  /**
   * 设置当前线程的视图类（替代原 {@code CURRENT_VIEW_CLASS.set(viewClass)}）。
   *
   * @param viewClass 视图类，null 表示清除视图过滤
   * @since 1.0.0
   */
  public static void setCurrentViewClass(Class<?> viewClass) {
    SerializationContext.CONTEXT.get().currentViewClass = viewClass;
  }

  /**
   * 获取当前线程的循环引用检测集合（替代原 {@code SERIALIZING_OBJECTS.get()}）。
   *
   * @return 当前线程的已序列化对象集合
   * @since 1.0.0
   */
  public static Set<Object> getSerializingObjects() {
    return SerializationContext.CONTEXT.get().serializingObjects;
  }

  /**
   * 获取当前线程的 StringBuilder 池实例（替代原 {@code SB_POOL.get()}）。
   *
   * @return 当前线程的 StringBuilder
   * @since 1.0.0
   */
  public static StringBuilder getSbPool() {
    return SerializationContext.CONTEXT.get().sbPool;
  }

  /**
   * 设置当前线程的 StringBuilder 池实例（替代原 {@code SB_POOL.set(sb)}）。
   *
   * @param sb 新的 StringBuilder 实例
   * @since 1.0.0
   */
  public static void setSbPool(StringBuilder sb) {
    SerializationContext.CONTEXT.get().sbPool = sb;
  }

  /**
   * 获取适合指定预估大小的 StringBuilder（大小分级策略）
   *
   * <p>根据预估的 JSON 大小选择合适容量的 StringBuilder，避免：
   *
   * <ul>
   *   <li>小 JSON 使用大 StringBuilder 浪费内存
   *   <li>大 JSON 使用小 StringBuilder 导致多次扩容
   * </ul>
   *
   * 分级阈值：
   *
   * <ul>
   *   <li>预估 < SMALL_SB_CAPACITY(1024)：小 JSON，适合简单 Bean
   *   <li>预估 < MEDIUM_SB_CAPACITY(4096)：中 JSON，适合一般 Bean
   *   <li>预估 < LARGE_SB_CAPACITY(16384)：大 JSON，适合大集合/复杂嵌套
   *   <li>预估 > LARGE_SB_CAPACITY：超大 JSON，按需分配
   * </ul>
   *
   * @param estimatedSize 预估的 JSON 输出大小
   * @return 适合大小的 StringBuilder
   */
  private static StringBuilder getSizedStringBuilder(int estimatedSize) {
    SerializationContext ctx = SerializationContext.CONTEXT.get();

    // 池化缓冲重入防护：嵌套序列化不复用 sbPool，防止外层/内层缓冲互相污染（P0 修复）
    if (ctx.poolDepth > 1) {
      return new StringBuilder(Math.max(estimatedSize, 256));
    }

    StringBuilder sb = ctx.sbPool;

    // 缩容保护：如果池中 StringBuilder 过大，根据预估大小缩容到合适的级别
    if (sb.capacity() > MAX_SB_CAPACITY) {
      int targetCapacity;
      if (estimatedSize <= SMALL_SB_CAPACITY) {
        targetCapacity = SMALL_SB_CAPACITY;
      } else if (estimatedSize <= MEDIUM_SB_CAPACITY) {
        targetCapacity = MEDIUM_SB_CAPACITY;
      } else if (estimatedSize <= LARGE_SB_CAPACITY) {
        targetCapacity = LARGE_SB_CAPACITY;
      } else {
        targetCapacity = MEDIUM_SB_CAPACITY; // 超大 JSON 缩容到中等，下次按需扩容
      }
      sb = new StringBuilder(targetCapacity);
      ctx.sbPool = sb;
    }

    // 扩容保护：如果预估大小超过当前容量，预分配
    if (estimatedSize > sb.capacity()) {
      sb.ensureCapacity(estimatedSize);
    }

    sb.setLength(0);
    return sb;
  }

  /**
   * 序列化对象
   *
   * @param obj 对象
   * @return JSON 字符串，不会为 {@code null}；{@code obj} 为 {@code null} 时返回字面量 {@code "null"}，
   *     {@code JsonNode} 树模型直接走 {@code toString()}，其余类型按 Bean 反射序列化
   */
  public static String serialize(Object obj) {
    if (obj == null) {
      return "null";
    }

    // JsonNode 树模型快速路径：ObjectNode/ArrayNode 等直接走树模型 toString()，
    // 避免被当作普通 Bean 反射序列化导致输出损坏（如 {"value":30}）
    if (obj instanceof JsonNode) {
      return obj.toString();
    }

    // @JsonSerialize 快速路径：如果类有 @JsonSerialize 注解，使用自定义序列化器
    Object customSerializer = getCustomSerializer(obj.getClass());
    if (customSerializer == null) {
      // P1-6：模块与直接注册的序列化器已统一写入 SerializerRegistry（单一事实源），
      // 直接查询全局注册中心，避免反向依赖 YdszJson（打破 YdszJson <-> SerializationProvider 循环依赖，1.2.1）
      customSerializer = SerializerRegistry.getInstance().get(obj.getClass());
    }
    if (customSerializer != null) {
      return invokeCustomSerializer(customSerializer, obj);
    }

    // @JsonValue 快速路径：如果类有 @JsonValue 标注的方法，直接序列化方法返回值
    Method jsonValueMethod = FieldMetadataLoader.findJsonValueMethod(obj.getClass());
    if (jsonValueMethod != null) {
      return recordJsonValueSerialization(obj, jsonValueMethod);
    }

    SerializationContext ctx = SerializationContext.CONTEXT.get();

    // 序列化深度安全网：超过阈值时抛出受控异常，替代 StackOverflowError
    int maxDepth = JsonConfig.copyOf(null).getMaxDepth();
    if (++ctx.serializationDepth > maxDepth) {
      ctx.serializationDepth--;
      throw new JsonSerializationException(
              JsonSerializationException.SERIALIZATION_ERROR,
              "Serialization depth exceeded maximum ("
                  + maxDepth
                  + "): "
                  + "object graph too deep or contains circular references")
          .setFieldPath(getCurrentFieldPath());
    }

    // 循环引用检测（仅对 Bean 类型，不含基本类型/Collection/Map/String/Number/Boolean）
    Class<?> clazz = obj.getClass();
    boolean isBeanType =
        !clazz.isPrimitive()
            && !clazz.isArray()
            && !(obj instanceof Number)
            && !(obj instanceof CharSequence)
            && !(obj instanceof Boolean)
            && !(obj instanceof Character)
            && !(obj instanceof Collection)
            && !(obj instanceof Map)
            && !(obj instanceof Enum);

    // 快速路径：如果 BeanSerializer 已缓存且标记为 primitiveOnly（无嵌套对象字段），
    // 则不可能产生循环引用，跳过 serializingObjects 的 add/remove 和 contains 检查。
    // 纯原始类型 Bean 在大对象图中数量最多，此举可显著降低 IdentityHashMap 压力。
    // @since 1.0.0
    BeanSerializer primitiveSerializer = null;
    if (isBeanType) {
      PropertyNamingStrategy strategyForLookup = FieldMetadataLoader.NAMING_STRATEGY.get();
      primitiveSerializer = BeanSerializerCache.get(clazz, strategyForLookup);
    }

    Set<Object> objects = ctx.serializingObjects;
    // 仅当 BeanSerializer 未缓存或不是 pure-primitive 时才进入循环引用检测
    boolean skipCycleDetection = primitiveSerializer != null && primitiveSerializer.primitiveOnly;
    // 确保 serializingObjects 使用 identity-based 比较（检测循环引用的核心）
    if (isBeanType && !skipCycleDetection) {
      if (objects.contains(obj)) {
        String strategy = ctx.circularRefStrategy;
        if (strategy == null) {
          strategy = "REF";
        }
        switch (strategy) {
          case "ERROR":
            // 回滚深度计数并抛出受控异常（非 StackOverflowError）
            ctx.serializationDepth--;
            throw new JsonSerializationException(
                    JsonSerializationException.SERIALIZATION_ERROR,
                    "Circular reference detected: " + clazz.getName())
                .setFieldPath(getCurrentFieldPath());
          case "IGNORE":
          case "NULL":
            return "null";
          case "REF":
          default:
            return "{\"$ref\":\"..\"}";
        }
      }
      objects.add(obj);
    }

    try {
      // 池化缓冲重入防护：最外层（poolDepth == 1）才允许复用 fastWriterPool / sbPool
      ctx.poolDepth++;

      // 统一快速路径：Bean/Collection/Map 三条路径提取到 tryFastPathToWriter
      JSONWriter writer = tryFastPathToWriter(obj, ctx);
      if (writer != null) {
        return writer.toString();
      }

      // 回退路径：使用 StringBuilder + ValueWriter
      StringBuilder sb = getSizedStringBuilder(256);

      if (!tryBeanSerialize(obj, sb)) {
        // ValueWriter.writeValue → writeBeanWithCycleDetection 有自己的循环引用检测，
        // 需先从 objects 中移除当前对象，避免误判为循环引用
        if (isBeanType && !skipCycleDetection) {
          objects.remove(obj);
        }
        ValueWriter.writeValue(obj, sb);
      }

      return sb.toString();
    } catch (JsonSerializationException e) {
      throw e;
    } catch (StackOverflowError e) {
      // 字节码级序列化路径可能绕过 serialize() 递归检测，
      // 在顶层捕获 StackOverflowError 并转换为有意义的异常
      throw new JsonSerializationException(
              JsonSerializationException.SERIALIZATION_ERROR,
              "Stack overflow during serialization: object graph too deep or circular reference",
              e)
          .setFieldPath(getCurrentFieldPath());
    } catch (Exception e) {
      throw wrapSerializationException(obj, e);
    } finally {
      ctx.poolDepth--;
      if (isBeanType && !skipCycleDetection) {
        objects.remove(obj);
      }
      // 回滚深度计数
      ctx.serializationDepth--;
    }
  }

  /**
   * 序列化对象（带特性配置）
   *
   * <p>当 PrettyPrint 特性未启用时，复用 {@link #serialize(Object)} 的快速路径； 启用 PrettyPrint 时走格式化路径。
   *
   * @param obj 对象
   * @param features 特性标志（位运算值）
   * @return JSON 字符串
   */
  public static String serialize(Object obj, long features) {
    if (obj == null) {
      return "null";
    }

    // 非 PrettyPrint 场景复用标准序列化路径
    if (!JSONWriter.Feature.PrettyPrint.isEnabled(features)) {
      return serialize(obj);
    }

    // 池化缓冲重入防护
    SerializationContext ctx = SerializationContext.CONTEXT.get();
    ctx.poolDepth++;
    try {
      // PrettyPrint 格式化路径
      StringBuilder sb = getSizedStringBuilder(LARGE_SB_CAPACITY);

      Set<Object> objects = ctx.serializingObjects;
      objects.clear();

      try {
        ValueFormatter.formatValue(obj, sb, 0);
      } catch (JsonSerializationException e) {
        throw e;
      } catch (Exception e) {
        throw wrapSerializationException(obj, e);
      } finally {
        objects.clear();
      }

      return sb.toString();
    } finally {
      ctx.poolDepth--;
    }
  }

  /**
   * 格式化序列化（带缩进）
   *
   * @param obj 对象
   * @return 带缩进换行的 JSON 字符串，不会为 {@code null}；{@code obj} 为 {@code null} 时返回字面量
   *     {@code "null"}。输出比 {@link #serialize(Object)} 体积更大，仅用于日志与调试
   */
  public static String format(Object obj) {
    if (obj == null) {
      return "null";
    }

    // 池化缓冲重入防护
    SerializationContext ctx = SerializationContext.CONTEXT.get();
    ctx.poolDepth++;
    try {
      // 格式化输出通常更大，使用较大的预估大小
      StringBuilder sb = getSizedStringBuilder(LARGE_SB_CAPACITY);

      Set<Object> objects = ctx.serializingObjects;
      objects.clear();

      try {
        ValueFormatter.formatValue(obj, sb, 0);
      } finally {
        objects.clear();
      }

      return sb.toString();
    } finally {
      ctx.poolDepth--;
    }
  }

  /**
   * 序列化对象（带视图过滤）
   *
   * @param obj 要序列化的对象
   * @param viewClass 视图类
   * @return JSON 字符串
   */
  public static String serializeWithView(Object obj, Class<?> viewClass) {
    if (obj == null) {
      return "null";
    }

    // 池化缓冲重入防护
    SerializationContext ctx = SerializationContext.CONTEXT.get();
    ctx.poolDepth++;
    try {
      StringBuilder sb = getSizedStringBuilder(256);

      Set<Object> objects = ctx.serializingObjects;
      objects.clear();

      Class<?> previousView = ctx.currentViewClass;
      ctx.currentViewClass = viewClass;
      try {
        ValueWriter.writeValue(obj, sb);
      } catch (Exception e) {
        throw wrapSerializationException(obj, e);
      } finally {
        ctx.currentViewClass = previousView;
        objects.clear();
      }

      return sb.toString();
    } finally {
      ctx.poolDepth--;
    }
  }

  /**
   * 序列化对象（带视图过滤和格式化）
   *
   * @param obj 要序列化的对象
   * @param viewClass 视图类
   * @param pretty 是否格式化
   * @return JSON 字符串
   */
  public static String serializeWithView(Object obj, Class<?> viewClass, boolean pretty) {
    if (obj == null) {
      return "null";
    }

    // 池化缓冲重入防护
    SerializationContext ctx = SerializationContext.CONTEXT.get();
    ctx.poolDepth++;
    try {
      // 格式化输出使用较大预估大小
      StringBuilder sb = getSizedStringBuilder(pretty ? LARGE_SB_CAPACITY : 256);

      Set<Object> objects = ctx.serializingObjects;
      objects.clear();

      Class<?> previousView = ctx.currentViewClass;
      ctx.currentViewClass = viewClass;
      try {
        if (pretty) {
          ValueFormatter.formatValue(obj, sb, 0);
        } else {
          ValueWriter.writeValue(obj, sb);
        }
      } catch (JsonSerializationException e) {
        throw e;
      } catch (Exception e) {
        throw wrapSerializationException(obj, e);
      } finally {
        ctx.currentViewClass = previousView;
        objects.clear();
      }

      return sb.toString();
    } finally {
      ctx.poolDepth--;
    }
  }

  /**
   * 序列化对象为 UTF-8 字节数组（零拷贝优化版）
   *
   * <p>直接使用 {@link JSONWriter#toUtf8Bytes()} 将 char[] 转为 byte[]， 跳过 String 中间层，避免双重分配（String +
   * getBytes）。 对于纯 ASCII 内容直接 1:1 拷贝，非 ASCII 回退到标准 UTF-8 编码。
   *
   * @param obj 要序列化的对象
   * @return UTF-8 编码的字节数组
   * @since 1.0.0
   */
  public static byte[] serializeToBytes(Object obj) {
    if (obj == null) {
      return new byte[] {'n', 'u', 'l', 'l'};
    }

    SerializationContext ctx = SerializationContext.CONTEXT.get();

    // 池化缓冲重入防护：最外层（poolDepth == 1）才允许复用 fastWriterPool / sbPool
    ctx.poolDepth++;
    try {
      // 统一快速路径：Bean/Collection/Map 三条路径提取到 tryFastPathToWriter
      JSONWriter writer = tryFastPathToWriter(obj, ctx);
      if (writer != null) {
        return writer.toUtf8Bytes();
      }

      // 回退路径：使用 StringBuilder + ValueWriter
      StringBuilder sb = getSizedStringBuilder(256);
      Set<Object> objects = ctx.serializingObjects;
      objects.clear();

      try {
        if (!tryBeanSerialize(obj, sb)) {
          ValueWriter.writeValue(obj, sb);
        }
      } catch (Exception e) {
        throw wrapSerializationException(obj, e);
      } finally {
        objects.clear();
      }

      return sb.toString().getBytes(StandardCharsets.UTF_8);
    } finally {
      ctx.poolDepth--;
    }
  }

  /**
   * 序列化对象并直接写入 OutputStream（避免中间 byte[] 分配）。
   *
   * <p>先序列化为 JSON 字符串，再通过 OutputStream 直接写入 UTF-8 字节， 对于大数据量场景可减少一次 byte[] 分配和 GC 压力。
   *
   * @param obj 要序列化的对象
   * @param out 输出流
   * @since 1.0.0
   */
  public static void serializeToStream(Object obj, OutputStream out) {
    if (obj == null) {
      try {
        out.write(new byte[] {'n', 'u', 'l', 'l'});
      } catch (IOException e) {
        throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR,
                "Failed to write null to OutputStream",
                e)
            .setFieldPath(getCurrentFieldPath());
      }
      return;
    }

    // 优先使用零拷贝 byte[] 路径，再写入流
    byte[] bytes = serializeToBytes(obj);
    try {
      out.write(bytes);
    } catch (IOException e) {
      throw new JsonSerializationException(
              JsonSerializationException.SERIALIZATION_ERROR, "Failed to write to OutputStream", e)
          .setFieldPath(getCurrentFieldPath());
    }
  }

  /**
   * 序列化对象并直接写入 Writer（避免中间 String 分配）。
   *
   * <p>先序列化为 JSON 字符串，再通过 Writer 写入字符流。 对于需要输出到 FileWriter/PrintWriter 等场景可减少一次 String 分配。
   *
   * @param obj 要序列化的对象
   * @param writer 字符输出流
   * @since 1.0.0
   */
  public static void serializeToWriter(Object obj, Writer writer) {
    if (obj == null) {
      try {
        writer.write("null");
      } catch (IOException e) {
        throw new JsonSerializationException(
                JsonSerializationException.SERIALIZATION_ERROR, "Failed to write null to Writer", e)
            .setFieldPath(getCurrentFieldPath());
      }
      return;
    }

    String json = serialize(obj);
    try {
      writer.write(json);
    } catch (IOException e) {
      throw new JsonSerializationException(
              JsonSerializationException.SERIALIZATION_ERROR, "Failed to write to Writer", e)
          .setFieldPath(getCurrentFieldPath());
    }
  }

  /**
   * 集合/Map 快速路径：尝试将 Collection/Map 直接序列化到 JSONWriter。
   *
   * <p>注意：此方法<b>仅对 Collection / Map 提供快速路径</b>；Bean 类型与数组 统一返回 null 回退到 StringBuilder 路径（{@code
   * tryBeanSerialize}）。 Bean 分支中的 FieldMeta 加载仅作为元数据预热副作用（保证命名策略维度缓存被填充）， 并非快速路径实现。
   *
   * @param obj 要序列化的对象（非 null）
   * @param ctx 当前线程的序列化上下文
   * @return 已写入内容的 JSONWriter，或 null 表示需要回退到 StringBuilder 路径
   */
  private static JSONWriter tryFastPathToWriter(Object obj, SerializationContext ctx) {
    Class<?> clazz = obj.getClass();

    // JsonNode 树模型：由 serialize() 入口直接 toString() 处理，这里不拦截
    if (obj instanceof JsonNode) {
      return null;
    }

    // 简单类型（Number/CharSequence/Boolean/Character/Enum）跳过 Bean 路径，
    // 由 ValueWriter.writeValue 统一处理
    if (isSimpleType(obj)) {
      return null;
    }

    // 快速路径 1：Collection / Map 之外的 Bean 类型
    if (!(obj instanceof Collection) && !(obj instanceof Map) && !clazz.isArray()) {
      // 获取当前线程的命名策略
      PropertyNamingStrategy strategy = FieldMetadataLoader.NAMING_STRATEGY.get();

      // 仅作元数据预热：确保 SerializerCache 被填充（无论是否使用快速路径），
      // 这样不同命名策略可以按 strategy 维度隔离缓存 FieldMeta[]
      FieldMeta[] fields = SerializerCache.getFieldMeta(clazz, strategy);
      if (fields == null) {
        fields = FieldMetadataLoader.loadFields(clazz);
        SerializerCache.putFieldMeta(clazz, strategy, fields);
      }

      return null; // Bean 类型统一回退到 StringBuilder 路径（tryBeanSerialize）
    }

    // 快速路径 2：Collection 类型直接使用 JSONWriter
    if (obj instanceof Collection) {
      // 池化缓冲重入防护：嵌套序列化使用独立实例（P0 修复）
      JSONWriter writer = ctx.poolDepth > 1 ? new JSONWriter() : ctx.fastWriterPool;
      writer.reset();
      Collection<?> coll = (Collection<?>) obj;
      if (!coll.isEmpty()) {
        writer.preAllocate(coll.size() * 64);
      }
      writer.writeCollection(coll);
      return writer;
    }

    // 快速路径 3：Map 类型直接使用 JSONWriter
    if (obj instanceof Map) {
      // 池化缓冲重入防护：嵌套序列化使用独立实例（P0 修复）
      JSONWriter writer = ctx.poolDepth > 1 ? new JSONWriter() : ctx.fastWriterPool;
      writer.reset();
      writer.writeMap((Map<?, ?>) obj);
      return writer;
    }

    return null; // 数组等其他类型，回退到 StringBuilder 路径
  }

  /**
   * 判断对象是否为简单类型（非 Bean），应由 ValueWriter 而非 BeanSerializer 处理。
   *
   * <p>包括：Number、CharSequence、Boolean、Character、Enum、java.util.Date、 java.time.temporal.Temporal
   * 及其子类。
   */
  private static boolean isSimpleType(Object obj) {
    return obj instanceof Number
        || obj instanceof CharSequence
        || obj instanceof Boolean
        || obj instanceof Character
        || obj instanceof Enum
        || obj instanceof Date
        || obj instanceof Temporal;
  }

  /**
   * Bean 序列化快速路径（BeanSerializer 路径，不重试 ）。
   *
   * <p>原 {@code tryFastSerialize} 方法中包含冗余的 调用——已在 {@link #tryFastPathToWriter}
   * 中尝试过，此处不再重复调用。 仅保留 BeanSerializer 路径作为 降级后的快速序列化方案。
   *
   * @param obj 要序列化的对象
   * @param sb StringBuilder 缓冲区
   * @return true 如果使用了 BeanSerializer 快速路径
   */
  private static boolean tryBeanSerialize(Object obj, StringBuilder sb) {
    if (obj == null) {
      return false;
    }

    Class<?> clazz = obj.getClass();

    // 简单类型由 ValueWriter 处理
    if (isSimpleType(obj)) {
      return false;
    }

    // 排除集合、Map、数组类型
    if (obj instanceof Collection || obj instanceof Map || clazz.isArray()) {
      return false;
    }

    // 检查是否可以使用快速路径
    JsonClass classAnnotation = clazz.getAnnotation(JsonClass.class);
    if (classAnnotation != null) {
      return false;
    }

    // 检查是否有视图过滤
    if (SerializationContext.CONTEXT.get().currentViewClass != null) {
      return false;
    }

    // 获取或创建 BeanSerializer（按当前线程命名策略隔离缓存）
    PropertyNamingStrategy strategy = FieldMetadataLoader.NAMING_STRATEGY.get();
    FieldMeta[] fields = SerializerCache.getFieldMeta(clazz, strategy);
    if (fields == null) {
      fields = FieldMetadataLoader.loadFields(clazz);
      SerializerCache.putFieldMeta(clazz, strategy, fields);
    }

    // 检查是否有字段注解（使用按策略隔离的 BeanSerializerInfo）
    BeanSerializerInfo serializerInfo = getOrCreateBeanSerializer(clazz, fields, strategy);
    if (serializerInfo.hasAnnotations) {
      return false;
    }

  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
    // 使用 JSONWriter 进行快速序列化（复用 ThreadLocal 池）
  // CHECKSTYLE.ON: RegexpSinglelineJava
    // 池化缓冲重入防护：嵌套序列化使用独立实例，避免 reset() 清空外层内容（P0 修复）
    SerializationContext serializeCtx = SerializationContext.CONTEXT.get();
    JSONWriter writer = serializeCtx.poolDepth > 1 ? new JSONWriter() : serializeCtx.fastWriterPool;
    writer.reset();

    BeanSerializer beanSerializer = BeanSerializerCache.getOrCreate(clazz, fields, strategy);

    beanSerializer.write(obj, writer);
    sb.append(writer.toString());
    return true;
  }

  /** Bean 序列化信息（预计算） */
  static final class BeanSerializerInfo {
    /** 有效字段数组（跳过 shouldSkip 的字段） */
    final FieldMeta[] validFields;

    /** 预估的 JSON 大小 */
    final int estimatedSize;

    /** 是否有字段注解（缓存 FieldMetadataLoader.hasFieldAnnotations 结果） */
    final boolean hasAnnotations;

    BeanSerializerInfo(FieldMeta[] validFields, int estimatedSize, boolean hasAnnotations) {
      this.validFields = validFields;
      this.estimatedSize = estimatedSize;
      this.hasAnnotations = hasAnnotations;
    }
  }

  /**
   * 获取或创建 BeanSerializerInfo（按当前线程命名策略隔离缓存）
   *
   * @param clazz 目标类
   * @param fields 字段元数据（由调用方按当前策略加载）
   * @param strategy 当前线程的命名策略
   */
  static BeanSerializerInfo getOrCreateBeanSerializer(
      Class<?> clazz, FieldMeta[] fields, PropertyNamingStrategy strategy) {
    ConcurrentMap<PropertyNamingStrategy, BeanSerializerInfo> strategyMap =
        BEAN_SERIALIZER_INFO_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
    BeanSerializerInfo info = strategyMap.get(strategy);
    if (info == null) {
      // 计算有效字段
      int count = fields.length;

      FieldMeta[] validFields = new FieldMeta[count];
      int idx = 0;
      int estimatedSize = 2; // {}

      for (FieldMeta field : fields) {
        validFields[idx++] = field;
        estimatedSize += field.jsonKeyLen + 16;
      }

      info =
          new BeanSerializerInfo(
              validFields, estimatedSize, FieldMetadataLoader.hasFieldAnnotations(fields));
      strategyMap.put(strategy, info);
    }
    return info;
  }

  /**
   * @JsonSerialize 自定义序列化器缓存（Class -> JsonSerializer 实例）。 有界 LRU（容量 1024），防止无界增长。
   */
  private static final BoundedLruCache<Class<?>, JsonSerializer<?>> CUSTOM_SERIALIZER_CACHE =
      new BoundedLruCache<>(1024);

  /**
   * 检查类是否有 @JsonSerialize 注解并获取自定义序列化器。
   *
   * @param clazz 要检查的类
   * @return 自定义序列化器实例，或 null 如果没有
   */
  private static JsonSerializer<?> getCustomSerializer(Class<?> clazz) {
    JsonSerialize annotation = clazz.getAnnotation(JsonSerialize.class);
    if (annotation == null || annotation.using() == Void.class) {
      return null;
    }
    try {
      return CUSTOM_SERIALIZER_CACHE.computeIfAbsent(
          clazz,
          c -> {
            try {
              return (JsonSerializer<?>) annotation.using().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
              throw new JsonSerializationException(
                      JsonSerializationException.SERIALIZATION_ERROR,
                      "Failed to instantiate custom serializer: " + annotation.using().getName(),
                      e)
                  .setFieldPath(getCurrentFieldPath());
            }
          });
    } catch (JsonSerializationException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonSerializationException(
              JsonSerializationException.SERIALIZATION_ERROR,
              "Failed to instantiate custom serializer: " + annotation.using().getName(),
              e)
          .setFieldPath(getCurrentFieldPath());
    }
  }

  /**
   * 调用自定义序列化器，直接写入 JSONWriter 后输出字符串。
   *
   * <p>自定义序列化器统一实现 {@link com.njydsz.common.json.serializer.JsonSerializer}， 直接写入 {@link
   * JSONWriter}，零拷贝、避免中间 String 分配。
   *
   * @param serializer 自定义序列化器实例
   * @param value 要序列化的对象
   * @return JSON 字符串
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static String invokeCustomSerializer(Object serializer, Object value) {
    JSONWriter out = new JSONWriter(new StringBuilder(256));
    ((JsonSerializer) serializer).serialize(value, out);
    return out.toString();
  }

  /**
   * @JsonValue 序列化路径：调用标注了 @JsonValue 的方法，序列化其返回值。
   *
   * <p>对标 Jackson @JsonValue：方法的返回值作为整个对象的 JSON 值， 跳过字段级序列化。常用于枚举自定义序列化（如返回枚举的 code 值而非 name）。
   *
   * @param obj 要序列化的对象
   * @param jsonValueMethod 标注了 @JsonValue 的方法
   * @return 方法返回值的 JSON 表示
   */
  private static String recordJsonValueSerialization(Object obj, Method jsonValueMethod) {
    try {
      Object value = jsonValueMethod.invoke(obj);
      if (value == null) {
        return "null";
      }
      // 递归序列化返回值（返回值可能是 String/Number/Boolean 等简单类型）
      return serialize(value);
    } catch (Exception e) {
      throw new JsonSerializationException(
              JsonSerializationException.SERIALIZATION_ERROR,
              "@JsonValue method invocation failed for "
                  + obj.getClass().getName()
                  + ": "
                  + e.getMessage(),
              e)
          .setFieldPath(getCurrentFieldPath());
    }
  }

  // ==================== 内部辅助方法 ====================

  /**
   * 创建带有自动诊断上下文的序列化异常。
   *
   * <p>自动从 {@link #FIELD_PATH_STACK} 捕获当前字段路径（如 "user.address.street"）， 附加到异常中供排障使用。对标 Jackson
   * {@code JsonMappingException.getPath()}。
   *
   * <p>推荐在 catch 块中统一使用：
   *
   * <pre>
   * // 替代之前的多个 throw new JsonSerializationException(...) 调用点
   * throw SerializationProvider.newSerializationException(
   *     JsonSerializationException.SERIALIZATION_ERROR,
   *     "Failed to write to OutputStream", e);
   * </pre>
   *
   * @param errorCode 错误码（参见 {@link JsonSerializationException} 常量）
   * @param message 错误消息（不含字段路径，方法自动附加）
   * @param cause 原始异常（可为 null）
   * @return 包含字段路径诊断信息的序列化异常
   * @since 1.0.0
   */
  public static JsonSerializationException newSerializationException(
      int errorCode, String message, Throwable cause) {
    String path = getCurrentFieldPath();
    String fullMessage = path.isEmpty() ? message : message + " (at path: " + path + ")";
    JsonSerializationException ex = new JsonSerializationException(errorCode, fullMessage, cause);
    if (!path.isEmpty()) {
      ex.setFieldPath(path);
    }
    return ex;
  }

  /**
   * 包装序列化异常（消除 serialize() 和 serializeWithView() 中的重复异常处理代码）。
   *
   * @param obj 正在序列化的对象
   * @param e 原始异常
   * @return 包装后的 JsonSerializationException
   */
  private static JsonSerializationException wrapSerializationException(Object obj, Exception e) {
    if (e instanceof JsonSerializationException jse) {
      // 附加字段路径信息（若存在）
      String path = getCurrentFieldPath();
      if (!path.isEmpty()) {
        jse.setFieldPath(path);
      }
      return jse;
    }
    // 携带字段路径的异常增强给排障带来极大便利
    String path = getCurrentFieldPath();
    String message =
        "Serialization failed for "
            + obj.getClass().getName()
            + (path.isEmpty() ? "" : " at path '" + path + "'")
            + ": "
            + e.getMessage();
    JsonSerializationException wrapped =
        new JsonSerializationException(JsonSerializationException.SERIALIZATION_ERROR, message, e);
    if (!path.isEmpty()) {
      wrapped.setFieldPath(path);
    }
    return wrapped;
  }
}
