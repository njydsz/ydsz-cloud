package com.njydsz.pmis.common.cache.export;

import com.njydsz.pmis.common.cache.api.Cache;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存导出导入工具类
 *
 * <p>核心功能：
 * <ul>
 *   <li>对象序列化导出：支持将缓存数据导出为序列化文件</li>
 *   <li>对象反序列化导入：支持从序列化文件导入缓存数据</li>
 *   <li>文本格式导出：支持将缓存数据导出为文本格式</li>
 *   <li>过滤导出：支持按条件过滤后导出</li>
 *   <li>限制导入：支持限制导入条目数量</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 导出到文件
 * CacheExportImport.exportCache(cache, "cache.dat");
 *
 * // 从文件导入
 * CacheExportImport.importCache(cache, "cache.dat");
 *
 * // 带过滤导出
 * CacheExportImport.exportCacheWithFilter(cache, "cache.dat",
 *     (key, value) -> key.toString().startsWith("user:"));
 *
 * // 限制数量导入
 * CacheExportImport.importCacheWithLimit(cache, "cache.dat", 1000);
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class CacheExportImport {

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

    public static <K, V> void importCache(
            Cache<K, V> cache, String filePath, Class<K> keyClass, Class<V> valueClass) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            Object obj = ois.readObject();
            if (!(obj instanceof Map)) {
                throw new ClassCastException("Expected Map, got " + obj.getClass().getName());
            }
            Map<?, ?> data = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : data.entrySet()) {
                K key = keyClass.cast(entry.getKey());
                V value = valueClass.cast(entry.getValue());
                cache.put(key, value);
            }
        }
    }

    public static <K extends Serializable, V extends Serializable> void exportCacheToText(
            Cache<K, V> cache, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (K key : cache.keySet()) {
                V value = cache.getIfPresent(key);
                if (value != null) {
                    writer.write(key.toString() + "\t" + value.toString());
                    writer.newLine();
                }
            }
        }
    }

    public static <K, V> void importCacheFromText(
            Cache<K, V> cache, String filePath, 
            TextParser<K, V> parser) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
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

    public static <K, V> int importCacheWithLimit(
            Cache<K, V> cache, String filePath, int maxEntries, Class<K> keyClass, Class<V> valueClass) throws IOException, ClassNotFoundException {
        int count = 0;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            Object obj = ois.readObject();
            if (!(obj instanceof Map)) {
                throw new ClassCastException("Expected Map, got " + obj.getClass().getName());
            }
            Map<?, ?> data = (Map<?, ?>) obj;
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

    public interface TextParser<K, V> {
        K parseKey(String text);
        V parseValue(String text);
    }

    @FunctionalInterface
    public interface CacheFilter<K, V> {
        boolean accept(K key, V value);
    }
}
