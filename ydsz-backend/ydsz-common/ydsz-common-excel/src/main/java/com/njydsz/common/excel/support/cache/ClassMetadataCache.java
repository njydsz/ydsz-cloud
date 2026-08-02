package com.njydsz.common.excel.support.cache;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 类元数据缓存 - 提升注解解析性能
 *
 * <p>采用单例模式,缓存类级别注解的解析结果。
 * 避免每次读写时重复解析@ExcelProperty等注解,显著提升性能。</p>
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li>读取缓存 - 存储类的读取元数据信息</li>
 *   <li>写入缓存 - 存储类的写入元数据信息</li>
 *   <li>线程安全 - 使用ConcurrentHashMap</li>
 *   <li>延迟加载 - 首次访问时初始化</li>
 * </ul>
 *
 * @see ClassMetadata
 * @see FieldInfo
 * @author ydsz-team
 * @since 1.0.0
 */
public class ClassMetadataCache {

    private static final ClassMetadataCache INSTANCE = new ClassMetadataCache();

    private final Map<Class<?>, ClassMetadata> readCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, ClassMetadata> writeCache = new ConcurrentHashMap<>();

    private ClassMetadataCache() {
    }

    public static ClassMetadataCache getInstance() {
        return INSTANCE;
    }

    public ClassMetadata getReadMetadata(Class<?> clazz) {
        return readCache.computeIfAbsent(clazz, this::buildReadMetadata);
    }

    public ClassMetadata getWriteMetadata(Class<?> clazz) {
        return writeCache.computeIfAbsent(clazz, this::buildWriteMetadata);
    }

    private ClassMetadata buildReadMetadata(Class<?> clazz) {
        ClassMetadata metadata = new ClassMetadata();
        metadata.setClazz(clazz);

        Field[] fields = clazz.getDeclaredFields();
        List<FieldInfo> fieldInfoList = new ArrayList<>();

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
            fieldInfo.setIndex(annotation.index() >= 0 ? annotation.index() : getFieldOrder(field, annotation));
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
        List<FieldInfo> fieldInfoList = new ArrayList<>();

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
            fieldInfo.setIndex(annotation.index() >= 0 ? annotation.index() : getFieldOrder(field, annotation));
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
     * <p>同时清空读取与写入两类缓存，一般在运行时模型热更新或插件化类加载后调用。
     * 该操作为全局副作用，会同时失效其他正在进行的读写任务所依赖的解析结果，调用方需确保此时无并发读写依赖旧缓存。</p>
     */
    public void clearCache() {
        readCache.clear();
        writeCache.clear();
    }

    /**
     * 仅清空指定类的元数据缓存。
     *
     * <p>用于定向失效单个模型的注解解析结果，避免全量刷新带来的抖动。
     * {@code clazz} 不可为 {@code null}，否则底层 {@link ConcurrentHashMap} 会抛出 {@link NullPointerException}。</p>
     *
     * @param clazz 需要失效缓存的目标类，非 {@code null}
     */
    public void clearCache(Class<?> clazz) {
        readCache.remove(clazz);
        writeCache.remove(clazz);
    }

    public int getCacheSize() {
        return readCache.size() + writeCache.size();
    }

    /**
     * 类元数据 - 存储类的字段映射信息
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
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

        public void setFieldInfoList(List<FieldInfo> fieldInfoList) {
            this.fieldInfoList = fieldInfoList;
            this.nameToIndexMap = new HashMap<>();
            for (int i = 0; i < fieldInfoList.size(); i++) {
                FieldInfo info = fieldInfoList.get(i);
                nameToIndexMap.put(info.getName(), i);
            }
        }

        public FieldInfo getFieldByIndex(int index) {
            if (index >= 0 && index < fieldInfoList.size()) {
                return fieldInfoList.get(index);
            }
            return null;
        }

        public FieldInfo getFieldByName(String name) {
            Integer index = nameToIndexMap.get(name);
            if (index != null) {
                return fieldInfoList.get(index);
            }
            return null;
        }

        public int getFieldCount() {
            return fieldInfoList != null ? fieldInfoList.size() : 0;
        }
    }

    /**
     * 字段信息 - 存储单个字段的映射元数据
     */
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