package com.njydsz.common.excel.support.cache;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 类元数据缓存 - 提升注解解析性能
 *
 * <p>采用单例模式,缓存类级别注解的解析结果。 避免每次读写时重复解析@ExcelProperty等注解,显著提升性能。
 *
 * <h3>缓存策略</h3>
 *
 * <ul>
 *   <li>读取缓存 - 存储类的读取元数据信息
 *   <li>写入缓存 - 存储类的写入元数据信息
 *   <li>线程安全 - 使用ConcurrentHashMap
 *   <li>延迟加载 - 首次访问时初始化
 * </ul>
 *
 * @see ClassMetadata
 * @see FieldInfo
 * @author ydsz-team
 * @since 26.09.01
 */
public class ClassMetadataCache {

  private static final ClassMetadataCache INSTANCE = new ClassMetadataCache();

  private final Map<Class<?>, ClassMetadata> readCache = new ConcurrentHashMap<>();
  private final Map<Class<?>, ClassMetadata> writeCache = new ConcurrentHashMap<>();

  private ClassMetadataCache() {}

  public static ClassMetadataCache getInstance() {
    return INSTANCE;
  }

  /**
   * 获取类的读取元数据（缓存优先）。
   *
   * <p>首次访问时基于 {@code @ExcelProperty}/{@code @ExcelIgnore} 注解解析构建并写入
   * 读取缓存，后续直接返回缓存实例。读取与写入元数据分别缓存、互不干扰。
   *
   * @param clazz 目标类，不可为 {@code null}
   * @return 读取元数据，永不为 {@code null}
   */
  public ClassMetadata getReadMetadata(Class<?> clazz) {
    return readCache.computeIfAbsent(clazz, this::buildReadMetadata);
  }

  /**
   * 获取类的写入元数据（缓存优先）。
   *
   * <p>与 {@link #getReadMetadata(Class)} 类似，但额外解析 {@code width} 等仅写入阶段 需要的属性。
   *
   * @param clazz 目标类，不可为 {@code null}
   * @return 写入元数据，永不为 {@code null}
   */
  public ClassMetadata getWriteMetadata(Class<?> clazz) {
    return writeCache.computeIfAbsent(clazz, this::buildWriteMetadata);
  }

  private ClassMetadata buildReadMetadata(Class<?> clazz) {
    ClassMetadata metadata = new ClassMetadata();
    metadata.setClazz(clazz);

    Field[] fields = clazz.getDeclaredFields();
    List<FieldInfo> fieldInfoList = new ArrayList<>(16);

    for (Field field : fields) {
      if (field.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }

      ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
      if (annotation == null) {
        continue;
      }

      FieldInfo fieldInfo = new FieldInfo();
      fieldInfo.setField(field);
      fieldInfo.setName(getExcelPropertyName(field, annotation));
      fieldInfo.setIndex(
          annotation.index() >= 0 ? annotation.index() : getFieldOrder(field, annotation));
      fieldInfo.setDateFormat(annotation.dateFormat());
      fieldInfo.setOrder(annotation.order());

      field.setAccessible(true);
      fieldInfoList.add(fieldInfo);
    }

    fieldInfoList.sort(Comparator.comparingInt(f -> f.getOrder()));
    metadata.setFieldInfoList(fieldInfoList);

    return metadata;
  }

  private ClassMetadata buildWriteMetadata(Class<?> clazz) {
    ClassMetadata metadata = new ClassMetadata();
    metadata.setClazz(clazz);

    Field[] fields = clazz.getDeclaredFields();
    List<FieldInfo> fieldInfoList = new ArrayList<>(16);

    for (Field field : fields) {
      if (field.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }

      ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
      if (annotation == null) {
        continue;
      }

      FieldInfo fieldInfo = new FieldInfo();
      fieldInfo.setField(field);
      fieldInfo.setName(getExcelPropertyName(field, annotation));
      fieldInfo.setIndex(
          annotation.index() >= 0 ? annotation.index() : getFieldOrder(field, annotation));
      fieldInfo.setDateFormat(annotation.dateFormat());
      fieldInfo.setWidth(annotation.width());
      fieldInfo.setOrder(annotation.order());

      field.setAccessible(true);
      fieldInfoList.add(fieldInfo);
    }

    fieldInfoList.sort(Comparator.comparingInt(f -> f.getOrder()));
    metadata.setFieldInfoList(fieldInfoList);

    return metadata;
  }

  private String getExcelPropertyName(Field field, ExcelProperty annotation) {
    String value = annotation.value();
    if (value != null && !value.isEmpty()) {
      return value;
    }
    return field.getName();
  }

  private int getFieldOrder(Field field, ExcelProperty annotation) {
    if (annotation.index() >= 0) {
      return annotation.index();
    }
    return Integer.MAX_VALUE;
  }

  /**
   * 清空全部类元数据缓存。
   *
   * <p>同时清空读取与写入两类缓存，一般在运行时模型热更新或插件化类加载后调用。 该操作为全局副作用，会同时失效其他正在进行的读写任务所依赖的解析结果，调用方需确保此时无并发读写依赖旧缓存。
   */
  public void clearCache() {
    readCache.clear();
    writeCache.clear();
  }

  /**
   * 仅清空指定类的元数据缓存。
   *
   * <p>用于定向失效单个模型的注解解析结果，避免全量刷新带来的抖动。 {@code clazz} 不可为 {@code null}，否则底层 {@link ConcurrentHashMap}
   * 会抛出 {@link NullPointerException}。
   *
   * @param clazz 需要失效缓存的目标类，非 {@code null}
   */
  public void clearCache(Class<?> clazz) {
    readCache.remove(clazz);
    writeCache.remove(clazz);
  }

  /**
   * 统计当前缓存的类元数据总条目数。
   *
   * <p>为读取与写入两类缓存大小的总和，用于监控与调优；可在缓存自然增长或调用 {@link #clearCache()} 后观察其变化。
   *
   * @return 缓存条目总数，恒大于等于 0
   */
  public int getCacheSize() {
    return readCache.size() + writeCache.size();
  }

  /**
   * 类元数据 - 存储类的字段映射信息
   *
   * @author ydsz-team

   * @version 26.09.01
   */
  public static class ClassMetadata {
    private Class<?> clazz;
    private List<FieldInfo> fieldInfoList;
    private Map<String, Integer> nameToIndexMap;

    public Class<?> getClazz() {
      return clazz;
    }

    public void setClazz(Class<?> clazz) {
      this.clazz = clazz;
    }

    public List<FieldInfo> getFieldInfoList() {
      return fieldInfoList;
    }

    /**
     * 设置字段信息列表，并重建「名称 → 下标」索引。
     *
     * <p>列表顺序即列顺序（构建期已按 {@code order} 排序）；重建的索引供 {@link #getFieldByName(String)} 做 O(1) 查找。
     *
     * @param fieldInfoList 字段信息列表，不可为 {@code null}
     */
    public void setFieldInfoList(List<FieldInfo> fieldInfoList) {
      this.fieldInfoList = fieldInfoList;
      this.nameToIndexMap = new HashMap<>(16);
      for (int i = 0; i < fieldInfoList.size(); i++) {
        FieldInfo info = fieldInfoList.get(i);
        nameToIndexMap.put(info.getName(), i);
      }
    }

    /**
     * 按下标获取字段信息。
     *
     * <p>下标越界时返回 {@code null} 而非抛异常，便于调用方直接判空处理。
     *
     * @param index 列下标，从 0 开始
     * @return 对应字段信息；下标越界时返回 {@code null}
     */
    public FieldInfo getFieldByIndex(int index) {
      if (index >= 0 && index < fieldInfoList.size()) {
        return fieldInfoList.get(index);
      }
      return null;
    }

    /**
     * 按列名获取字段信息。
     *
     * <p>基于 {@link #setFieldInfoList(List)} 时建立的名称索引查找，名称大小写敏感； 列名不存在时返回 {@code null} 而非抛异常。
     *
     * @param name 列名
     * @return 对应字段信息；未找到时返回 {@code null}
     */
    public FieldInfo getFieldByName(String name) {
      Integer index = nameToIndexMap.get(name);
      if (index != null) {
        return fieldInfoList.get(index);
      }
      return null;
    }

    /**
     * 返回字段总数。
     *
     * <p>元数据尚未设置（{@code fieldInfoList} 为 {@code null}）时返回 0， 保证外部可安全调用。
     *
     * @return 字段数量，恒大于等于 0
     */
    public int getFieldCount() {
      return fieldInfoList != null ? fieldInfoList.size() : 0;
    }
  }

  /** 字段信息 - 存储单个字段的映射元数据 */
  public static class FieldInfo {
    private Field field;
    private String name;
    private int index;
    private String dateFormat;
    private int width;
    private int order;

    public Field getField() {
      return field;
    }

    public void setField(Field field) {
      this.field = field;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getIndex() {
      return index;
    }

    public void setIndex(int index) {
      this.index = index;
    }

    public String getDateFormat() {
      return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
      this.dateFormat = dateFormat;
    }

    public int getWidth() {
      return width;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    public int getOrder() {
      return order;
    }

    public void setOrder(int order) {
      this.order = order;
    }
  }
}
