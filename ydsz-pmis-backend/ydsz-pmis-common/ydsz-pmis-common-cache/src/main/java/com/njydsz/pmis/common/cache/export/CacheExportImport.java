package com.njydsz.pmis.common.cache.export;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.cache.api.Cache;

/**
 * 缓存导出导入工具类
 *
 * <p>核心功能：
 *
 * <ul>
 *   <li>对象序列化导出：支持将缓存数据导出为序列化文件
 *   <li>对象反序列化导入：支持从序列化文件导入缓存数据
 *   <li>文本格式导出：支持将缓存数据导出为文本格式
 *   <li>过滤导出：支持按条件过滤后导出
 *   <li>限制导入：支持限制导入条目数量
 * </ul>
 *
 * <p>安全优化（P1 修复）：
 *
 * <ul>
 *   <li>反序列化使用 {@link ObjectInputFilter} 白名单机制，仅允许 JDK 核心类型
 *   <li>限制反序列化深度（≤5）、引用数（≤500000）、字节数（≤256MB）
 *   <li>限制导入 Map 大小，防止 OOM 攻击
 * </ul>
 *
 * 
 */
public class CacheExportImport {

  /** 最大允许的反序列化深度 */
  private static final int MAX_DEPTH = 5;

  /** 最大允许的引用数量 */
  private static final long MAX_REFERENCES = 500_000L;

  /** 最大允许的反序列化字节数（256MB） */
  private static final long MAX_STREAM_BYTES = 256L * 1024 * 1024;

  /** 默认最大导入条目数 */
  private static final int DEFAULT_MAX_ENTRIES = 1_000_000;

  /** 反序列化白名单前缀 */
  private static final String[] ALLOWED_PACKAGE_PREFIXES = {
    "java.lang.",
    "java.util.",
    "java.math.",
    "java.time.",
    "java.lang.reflect.",
    "sun.reflect.annotation.",
  };

  /**
   * 创建安全的 ObjectInputStream，配置反序列化过滤器
   *
   * @param fis 文件输入流
   * @return 配置了安全过滤器的 ObjectInputStream
   * @throws IOException 如果创建流失败
   */
  private static ObjectInputStream createSafeObjectInputStream(FileInputStream fis)
      throws IOException {
    ObjectInputStream ois = new ObjectInputStream(fis);
    ois.setObjectInputFilter(
        filterInfo -> {
          // 限制反序列化深度
          if (filterInfo.depth() > MAX_DEPTH) {
            return ObjectInputFilter.Status.REJECTED;
          }
          // 限制引用数量
          if (filterInfo.references() > MAX_REFERENCES) {
            return ObjectInputFilter.Status.REJECTED;
          }
          // 限制字节数
          if (filterInfo.streamBytes() > MAX_STREAM_BYTES) {
            return ObjectInputFilter.Status.REJECTED;
          }
          // 白名单类检查
          if (filterInfo.serialClass() != null) {
            String className = filterInfo.serialClass().getName();
            // 数组类型：交给元素级检查（每个元素会单独触发 filter 回调）
            if (className.startsWith("[")) {
              return ObjectInputFilter.Status.UNDECIDED;
            }
            for (String prefix : ALLOWED_PACKAGE_PREFIXES) {
              if (className.startsWith(prefix)) {
                return ObjectInputFilter.Status.ALLOWED;
              }
            }
            return ObjectInputFilter.Status.REJECTED;
          }
          return ObjectInputFilter.Status.UNDECIDED;
        });
    return ois;
  }

  /**
   * 验证导入的 Map 大小
   *
   * @param data 导入的 Map
   * @param maxEntries 最大允许条目数
   * @throws IOException 如果超过限制
   */
  private static void validateMapSize(Map<?, ?> data, int maxEntries) throws IOException {
    if (data.size() > maxEntries) {
      throw new IOException("导入数据量超过限制: " + data.size() + " > " + maxEntries);
    }
  }

  /**
   * 导出缓存数据到序列化文件
   *
   * @param cache 缓存实例
   * @param filePath 文件路径
   * @param <K> 键类型（必须实现 Serializable）
   * @param <V> 值类型（必须实现 Serializable）
   * @throws IOException 如果导出失败
   */
  public static <K extends Serializable, V extends Serializable> void exportCache(
      Cache<K, V> cache, String filePath) throws IOException {
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
      Map<K, V> data = new HashMap<>();
      for (K key : cache.keySet()) {
        V value = cache.getIfPresent(key);
        if (value != null) {
          data.put(key, value);
        }
      }
      oos.writeObject(data);
    }
  }

  /**
   * 从序列化文件导入缓存数据
   *
   * @param cache 缓存实例
   * @param filePath 文件路径
   * @param keyClass 键类型
   * @param valueClass 值类型
   * @param <K> 键类型
   * @param <V> 值类型
   * @throws IOException 如果导入失败
   * @throws ClassNotFoundException 如果反序列化类未找到
   */
  public static <K, V> void importCache(
      Cache<K, V> cache, String filePath, Class<K> keyClass, Class<V> valueClass)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream ois = createSafeObjectInputStream(new FileInputStream(filePath))) {
      Object obj = ois.readObject();
      if (!(obj instanceof Map)) {
        throw new ClassCastException("Expected Map, got " + obj.getClass().getName());
      }
      Map<?, ?> data = (Map<?, ?>) obj;
      validateMapSize(data, DEFAULT_MAX_ENTRIES);
      for (Map.Entry<?, ?> entry : data.entrySet()) {
        K key = keyClass.cast(entry.getKey());
        V value = valueClass.cast(entry.getValue());
        cache.put(key, value);
      }
    }
  }

  /**
   * 导出缓存数据到文本文件
   *
   * @param cache 缓存实例
   * @param filePath 文件路径
   * @param <K> 键类型（必须实现 Serializable）
   * @param <V> 值类型（必须实现 Serializable）
   * @throws IOException 如果导出失败
   */
  public static <K extends Serializable, V extends Serializable> void exportCacheToText(
      Cache<K, V> cache, String filePath) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
      for (K key : cache.keySet()) {
        V value = cache.getIfPresent(key);
        if (value != null) {
          writer.write(key.toString() + "\t" + value.toString());
          writer.newLine();
        }
      }
    }
  }

  /**
   * 从文本文件导入缓存数据
   *
   * @param cache 缓存实例
   * @param filePath 文件路径
   * @param parser 文本解析器
   * @param <K> 键类型
   * @param <V> 值类型
   * @throws IOException 如果导入失败
   */
  public static <K, V> void importCacheFromText(
      Cache<K, V> cache, String filePath, TextParser<K, V> parser) throws IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\t", 2);
        if (parts.length == 2) {
          K key = parser.parseKey(parts[0]);
          V value = parser.parseValue(parts[1]);
          cache.put(key, value);
        }
      }
    }
  }

  /**
   * 带过滤条件导出缓存数据
   *
   * @param cache 缓存实例
   * @param filePath 文件路径
   * @param filter 过滤器
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 导出的条目数
   * @throws IOException 如果导出失败
   */
  public static <K, V> int exportCacheWithFilter(
      Cache<K, V> cache, String filePath, CacheFilter<K, V> filter) throws IOException {
    int count = 0;
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
      Map<K, V> data = new HashMap<>();
      for (K key : cache.keySet()) {
        V value = cache.getIfPresent(key);
        if (value != null && filter.accept(key, value)) {
          data.put(key, value);
          count++;
        }
      }
      oos.writeObject(data);
    }
    return count;
  }

  /**
   * 限制数量导入缓存数据
   *
   * @param cache 缓存实例
   * @param filePath 文件路径
   * @param maxEntries 最大导入条目数
   * @param keyClass 键类型
   * @param valueClass 值类型
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 实际导入的条目数
   * @throws IOException 如果导入失败
   * @throws ClassNotFoundException 如果反序列化类未找到
   */
  public static <K, V> int importCacheWithLimit(
      Cache<K, V> cache, String filePath, int maxEntries, Class<K> keyClass, Class<V> valueClass)
      throws IOException, ClassNotFoundException {
    int count = 0;
    try (ObjectInputStream ois = createSafeObjectInputStream(new FileInputStream(filePath))) {
      Object obj = ois.readObject();
      if (!(obj instanceof Map)) {
        throw new ClassCastException("Expected Map, got " + obj.getClass().getName());
      }
      Map<?, ?> data = (Map<?, ?>) obj;
      validateMapSize(data, DEFAULT_MAX_ENTRIES);
      for (Map.Entry<?, ?> entry : data.entrySet()) {
        if (count >= maxEntries) {
          break;
        }
        K key = keyClass.cast(entry.getKey());
        V value = valueClass.cast(entry.getValue());
        cache.put(key, value);
        count++;
      }
    }
    return count;
  }

  /**
   * 文本解析器接口
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  public interface TextParser<K, V> {
    K parseKey(String text);

    V parseValue(String text);
  }

  /**
   * 缓存过滤器接口
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  @FunctionalInterface
  public interface CacheFilter<K, V> {
    boolean accept(K key, V value);
  }
}
