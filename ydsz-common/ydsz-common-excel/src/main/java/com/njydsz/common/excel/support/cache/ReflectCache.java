package com.njydsz.common.excel.support.cache;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.support.asm.ASMFieldAccessor;

/**
 * 反射缓存 - 提升反射访问性能
 *
 * <p>通过缓存字段、方法句柄等反射相关对象，减少重复的反射调用开销。 支持字段缓存、Getter/Setter 方法句柄缓存。
 *
 * <h3>核心功能</h3>
 *
 * <ul>
 *   <li>字段缓存 - 缓存已设置可访问的 Field 对象
 *   <li>类字段缓存 - 缓存类的所有字段数组
 *   <li>Setter 句柄缓存 - 缓存字段的 setter MethodHandle
 *   <li>Getter 句柄缓存 - 缓存字段的 getter MethodHandle
 *   <li>FieldGetter 缓存 - 基于 MethodHandle 的高性能字段访问器
 * </ul>
 *
 * <h3>设计模式</h3>
 *
 * <ul>
 *   <li>享元模式 - 缓存反射对象复用
 *   <li>门面模式 - 统一管理多种反射缓存
 * </ul>
 *
 * <h3>性能优化</h3>
 *
 * <p>使用 MethodHandle 替代反射，获得接近直接调用的性能表现。 MethodHandle 由 JVM 内联优化，比传统反射快约 6 倍。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 获取缓存的字段（已设置 setAccessible）
 * Field field = ReflectCache.getCachedField(User.class, "name");
 *
 * // 获取缓存的字段数组
 * Field[] fields = ReflectCache.getCachedFields(User.class);
 *
 * // 获取缓存的 setter 方法句柄
 * MethodHandle setter = ReflectCache.getCachedSetter(User.class, "name", String.class);
 * setter.invoke(user, "张三");
 *
 * // 获取缓存的 getter 方法句柄
 * MethodHandle getter = ReflectCache.getCachedGetter(User.class, "name");
 * String name = (String) getter.invoke(user);
 *
 * // 获取高性能 FieldGetter 访问器
 * FieldGetter fieldGetter = ReflectCache.getFieldGetter(User.class, field);
 * Object value = fieldGetter.get(user);
 *
 * // 清空缓存
 * ReflectCache.clearCache();
 * }</pre>
 *
 * @see Field
 * @see MethodHandle
 * @see ASMFieldAccessor
 * @author ydsz-team
 * @since 26.09.01
 */
public class ReflectCache {

  private ReflectCache() {}

  /** 字段缓存: 类名#字段名 -> Field */
  private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

  /** 方法句柄缓存: 类名#set:字段名 -> MethodHandle */
  private static final Map<String, MethodHandle> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();

  /** 类字段数组缓存: 类名 -> Field[] */
  private static final Map<Class<?>, Field[]> CLASS_FIELDS_CACHE = new ConcurrentHashMap<>();

  /** ASM Getter缓存: 类名#字段名 -> ASMFieldAccessor.FieldGetter */
  private static final Map<String, ASMFieldAccessor.FieldGetter> ASM_GETTER_CACHE =
      new ConcurrentHashMap<>();

  /** ASM Setter缓存: 类名#字段名 -> ASMFieldAccessor.FieldSetter */
  private static final Map<String, ASMFieldAccessor.FieldSetter> ASM_SETTER_CACHE =
      new ConcurrentHashMap<>();

  /** ASM 实例化器缓存: 类名 -> ASMFieldAccessor.ObjectInstantiator */
  private static final Map<Class<?>, ASMFieldAccessor.ObjectInstantiator> ASM_INSTANTIATOR_CACHE =
      new ConcurrentHashMap<>();

  /**
   * 获取缓存的字段
   *
   * <p>如果缓存中存在则直接返回，否则查找字段并加入缓存
   *
   * @param clazz 目标类
   * @param fieldName 字段名
   * @return 字段对象（已设置 setAccessible）
   */
  public static Field getCachedField(Class<?> clazz, String fieldName) {
    String key = clazz.getName() + "#" + fieldName;
    return FIELD_CACHE.computeIfAbsent(
        key,
        k -> {
          try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
          } catch (NoSuchFieldException e) {
            return null;
          }
        });
  }

  /**
   * 获取缓存的类字段数组
   *
   * <p>缓存类的所有声明字段，并预先设置可访问
   *
   * @param clazz 目标类
   * @return 字段数组
   */
  public static Field[] getCachedFields(Class<?> clazz) {
    return CLASS_FIELDS_CACHE.computeIfAbsent(
        clazz,
        k -> {
          Field[] fields = clazz.getDeclaredFields();
          for (Field field : fields) {
            field.setAccessible(true);
          }
          return fields;
        });
  }

  /**
   * 获取缓存的 setter 方法句柄
   *
   * <p>优先尝试通过方法名查找 setter，如找不到则使用 field 反射
   *
   * @param clazz 目标类
   * @param fieldName 字段名
   * @param fieldType 字段类型
   * @return setter 方法句柄
   */
  public static MethodHandle getCachedSetter(Class<?> clazz, String fieldName, Class<?> fieldType) {
    String key = clazz.getName() + "#set:" + fieldName;
    return METHOD_HANDLE_CACHE.computeIfAbsent(
        key,
        k -> {
          try {
            String setterName = "set" + capitalize(fieldName);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType type = MethodType.methodType(void.class, clazz, fieldType);
            return lookup.findVirtual(clazz, setterName, type);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            try {
              Field field = clazz.getDeclaredField(fieldName);
              field.setAccessible(true);
              return MethodHandles.lookup().unreflectSetter(field);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
              return null;
            }
          }
        });
  }

  /**
   * 获取缓存的 getter 方法句柄
   *
   * <p>优先尝试通过方法名查找 getter，如找不到则使用 field 反射
   *
   * @param clazz 目标类
   * @param fieldName 字段名
   * @return getter 方法句柄
   */
  public static MethodHandle getCachedGetter(Class<?> clazz, String fieldName) {
    String key = clazz.getName() + "#get:" + fieldName;
    return METHOD_HANDLE_CACHE.computeIfAbsent(
        key,
        k -> {
          try {
            String getterName = "get" + capitalize(fieldName);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType type = MethodType.methodType(Object.class, clazz);
            return lookup.findVirtual(clazz, getterName, type);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            try {
              Field field = clazz.getDeclaredField(fieldName);
              field.setAccessible(true);
              return MethodHandles.lookup().unreflectGetter(field);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
              return null;
            }
          }
        });
  }

  /**
   * 首字母大写
   *
   * @param name 原始名称
   * @return 首字母大写后的名称
   */
  private static String capitalize(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  /**
   * 获取高性能 FieldGetter 访问器
   *
   * <p>基于 MethodHandle 实现，比原生反射快约 6 倍。
   *
   * @param clazz 目标类
   * @param field 目标字段
   * @return Getter访问器
   */
  public static ASMFieldAccessor.FieldGetter getFieldGetter(Class<?> clazz, Field field) {
    String key = clazz.getName() + "#" + field.getName();
    return ASM_GETTER_CACHE.computeIfAbsent(key, k -> ASMFieldAccessor.getGetter(clazz, field));
  }

  /**
   * 获取高性能 FieldSetter 访问器
   *
   * <p>基于 MethodHandle 实现，比原生反射快约 6 倍。
   *
   * @param clazz 目标类
   * @param field 目标字段
   * @return Setter访问器
   */
  public static ASMFieldAccessor.FieldSetter getFieldSetter(Class<?> clazz, Field field) {
    String key = clazz.getName() + "#" + field.getName() + "#setter";
    return ASM_SETTER_CACHE.computeIfAbsent(key, k -> ASMFieldAccessor.getSetter(clazz, field));
  }

  /**
   * 获取对象实例化器
   *
   * <p>基于反射 Constructor 实现。
   *
   * @param clazz 目标类
   * @return 对象实例化器
   */
  public static ASMFieldAccessor.ObjectInstantiator getInstantiator(Class<?> clazz) {
    return ASM_INSTANTIATOR_CACHE.computeIfAbsent(
        clazz, k -> ASMFieldAccessor.getInstantiator(clazz));
  }

  /**
   * 清空所有缓存
   *
   * @author ydsz-team

   * @version 26.09.01
   */
  public static void clearCache() {
    FIELD_CACHE.clear();
    METHOD_HANDLE_CACHE.clear();
    CLASS_FIELDS_CACHE.clear();
    ASM_GETTER_CACHE.clear();
    ASM_SETTER_CACHE.clear();
    ASM_INSTANTIATOR_CACHE.clear();
  }
}
