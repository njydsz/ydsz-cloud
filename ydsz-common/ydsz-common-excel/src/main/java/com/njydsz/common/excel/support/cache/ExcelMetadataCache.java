package com.njydsz.common.excel.support.cache;

import com.njydsz.common.excel.core.metadata.MetadataCache;

/**
 * Excel 模块统一元数据缓存入口 — 整合 ClassMetadataCache / ReflectCache / MetadataCache 三套缓存的对外管控。
 *
 * <h3>设计目的</h3>
 *
 * <p>ydsz-common-excel 内部存在三套职责部分重叠的缓存：
 *
 * <ul>
 *   <li>{@link ClassMetadataCache} — 类的读取/写入注解解析元数据（{@code @ExcelProperty} /
 *       {@code @ExcelIgnore} 扫描）
 *   <li>{@link ReflectCache} — 反射 MethodHandle 缓存（getter / setter / instantiator） +
 *       类字段数组缓存
 *   <li>{@link MetadataCache} (core.metadata) — fast 写入路径的预计算元数据（字段、列宽、公式）
 * </ul>
 *
 * <p>本外观类提供统一的 clear / 统计入口，便于全局容量管理和问题排查。具体的缓存存储策略仍由各自的实现类负责。
 *
 * <h3>调用示例</h3>
 *
 * <pre>{@code
 * // 热更新场景：全量刷新所有元数据缓存
 * ExcelMetadataCache.clearAll();
 *
 * // 监控：获取总缓存条目数
 * int total = ExcelMetadataCache.totalEntries();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ExcelMetadataCache {

  private ExcelMetadataCache() {}

  /**
   * 清空全部元数据缓存（ClassMetadataCache + ReflectCache + MetadataCache）。
   *
   * <p><b>注意</b>：全局副作用，会同时失效正在进行的读写任务所依赖的解析结果；
   * 一般在热更新或插件化类加载后调用。
   */
  public static void clearAll() {
    ClassMetadataCache.getInstance().clearCache();
    ReflectCache.clearCache();
    MetadataCache.clear();
  }

  /**
   * 仅清空指定类的元数据缓存（ClassMetadataCache 精确清理；ReflectCache / MetadataCache
   * 当前无按类清理 API，全量清空代替。调用方如仅需定向失效 ClassMetadataCache，可直接调用 {@link
   * ClassMetadataCache#clearCache(Class)}）。
   *
   * @param clazz 目标类（当前仅精确清理 ClassMetadataCache）
   */
  public static void clearClass(Class<?> clazz) {
    ClassMetadataCache.getInstance().clearCache(clazz);
  }

  /**
   * 返回所有缓存的总条目数（近似值，仅供参考，非实时精确值）。
   *
   * <p>用于监控与调优；三套缓存的统计口径不同（ClassMetadataCache 计 read+write 两套、
   * MetadataCache 按类型）。
   *
   * @return 缓存条目的近似总和
   */
  public static int totalEntries() {
    return ClassMetadataCache.getInstance().getCacheSize()
        + MetadataCache.getCacheSize();
  }
}
