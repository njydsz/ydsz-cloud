package com.njydsz.common.excel.core.writer;

import com.njydsz.common.excel.annotation.ExcelStyle;
import com.njydsz.common.excel.core.style.WriteStyleHandler;
import com.njydsz.common.excel.support.cache.LRUCache;
import org.apache.poi.ss.usermodel.CellStyle;

/**
 * 样式管理器 - 管理单元格样式缓存
 *
 * <p>使用LRU策略缓存单元格样式，避免相同样式重复创建， 同时限制缓存大小防止内存无限增长。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelWriter
 * @see WriteStyleHandler
 */
public class StyleManager {

  /** 单元格样式缓存 - 使用LRU策略避免无限增长 */
  private final LRUCache<ExcelStyle, CellStyle> cellStyleCache;

  /** 样式处理器 */
  private WriteStyleHandler styleHandler;

  /**
   * 构造方法
   *
   * @param cacheCapacity 缓存容量
   */
  public StyleManager(int cacheCapacity) {
    this.cellStyleCache = new LRUCache<>(cacheCapacity);
  }

  /**
   * 设置样式处理器
   *
   * @param styleHandler 样式处理器
   */
  public void setStyleHandler(WriteStyleHandler styleHandler) {
    this.styleHandler = styleHandler;
  }

  /**
   * 获取样式处理器
   *
   * @return 样式处理器
   */
  public WriteStyleHandler getStyleHandler() {
    return styleHandler;
  }

  /**
   * 获取或创建数据样式
   *
   * <p>如果缓存中存在则直接返回，否则通过样式处理器创建并缓存。
   *
   * @param styleAnnotation 样式注解
   * @return 单元格样式
   */
  public CellStyle getOrCreateDataStyle(ExcelStyle styleAnnotation) {
    if (styleAnnotation == null) {
      return styleHandler.getDataStyle(null);
    }
    return cellStyleCache.getOrLoad(styleAnnotation, s -> styleHandler.getDataStyle(s));
  }

  /**
   * 获取表头样式
   *
   * @param styleAnnotation 样式注解
   * @return 单元格样式
   */
  public CellStyle getHeadStyle(ExcelStyle styleAnnotation) {
    return styleHandler.getHeadStyle(styleAnnotation);
  }

  /** 清空样式缓存 */
  public void clearCache() {
    cellStyleCache.clear();
  }

  /**
   * 获取缓存大小
   *
   * @return 当前缓存中的条目数
   */
  public int cacheSize() {
    return cellStyleCache.size();
  }
}
