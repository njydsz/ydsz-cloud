package com.njydsz.common.exception.core;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 异常上下文，封装 level / category / snapshot / extData / timestamp 元数据。
 *
 * <p>集中管理异常的非 identity 字段（级别、分类、快照、附加数据、发生时间），
 * 避免污染 {@link com.njydsz.common.exception.custom.AbstractYdszException} 主类。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.exception.custom.AbstractYdszException
 */
public class ExceptionContext implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 异常级别 */
  private ExceptionLevel level;

  /** 异常分类 */
  private ExceptionCategory category;

  /** 异常链上下文快照（透写入 details，供排查定位） */
  private Map<String, String> snapshot;

  /** 附加数据（通过 Builder.data() 设置） */
  private Map<String, Object> extData;

  /** 异常抛出时间 */
  private LocalDateTime timestamp;

  /** 默认构造函数 */
  public ExceptionContext() {
    this.timestamp = LocalDateTime.now();
  }

  /**
   * 使用级别和分类构造异常上下文。
   *
   * @param level 异常级别
   * @param category 异常分类
   */
  public ExceptionContext(ExceptionLevel level, ExceptionCategory category) {
    this.level = level;
    this.category = category;
    this.timestamp = LocalDateTime.now();
  }

  /**
   * 获取异常级别。
   *
   * @return 异常级别；可能为 null
   */
  public ExceptionLevel getLevel() {
    return level;
  }

  /**
   * 设置异常级别。
   *
   * @param level 异常级别
   */
  public void setLevel(ExceptionLevel level) {
    this.level = level;
  }

  /**
   * 获取异常分类。
   *
   * @return 异常分类；可能为 null
   */
  public ExceptionCategory getCategory() {
    return category;
  }

  /**
   * 设置异常分类。
   *
   * @param category 异常分类
   */
  public void setCategory(ExceptionCategory category) {
    this.category = category;
  }

  /**
   * 获取异常上下文快照（不可变视图）。
   *
   * @return 不可变快照 Map；未设置时返回 null
   */
  public Map<String, String> getSnapshot() {
    return snapshot == null ? null : Collections.unmodifiableMap(snapshot);
  }

  /**
   * 设置快照 Map（覆盖式）。
   *
   * @param snapshot 快照 Map，可为 null
   */
  public void setSnapshot(Map<String, String> snapshot) {
    if (snapshot == null) {
      this.snapshot = null;
    } else {
      this.snapshot = new LinkedHashMap<>(snapshot);
    }
  }

  /**
   * 向上下文快照追加单个键值对（链式调用）。
   *
   * @param key 快照键，不可为 null
   * @param value 快照值（自动 String.valueOf 转换），可为 null
   * @return 当前异常上下文，便于链式调用
   */
  public ExceptionContext addSnapshot(String key, Object value) {
    if (this.snapshot == null) {
      this.snapshot = new LinkedHashMap<>(16);
    }
    this.snapshot.put(key, String.valueOf(value));
    return this;
  }

  /**
   * 获取附加数据（不可变视图）。
   *
   * @return 不可变附加数据 Map；未设置时返回空 Map
   */
  public Map<String, Object> getData() {
    return extData == null ? Collections.emptyMap() : Collections.unmodifiableMap(extData);
  }

  /**
   * 设置附加数据（覆盖式）。
   *
   * @param data 附加数据 Map，可为 null
   */
  public void setData(Map<String, Object> data) {
    if (data == null) {
      this.extData = null;
    } else {
      this.extData = new LinkedHashMap<>(data);
    }
  }

  /**
   * 追加单个附加数据（链式调用）。
   *
   * @param key 键
   * @param value 值
   * @return 当前异常上下文，便于链式调用
   */
  public ExceptionContext addData(String key, Object value) {
    if (this.extData == null) {
      this.extData = new LinkedHashMap<>(16);
    }
    this.extData.put(key, value);
    return this;
  }

  /**
   * 获取异常抛出时间。
   *
   * @return 异常抛出时间
   */
  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  /**
   * 设置异常抛出时间。
   *
   * @param timestamp 抛出时间
   */
  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }
}
