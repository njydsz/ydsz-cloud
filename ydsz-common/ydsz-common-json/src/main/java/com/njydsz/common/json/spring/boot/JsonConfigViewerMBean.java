package com.njydsz.common.json.spring.boot;

/**
 * YdszJson 配置 JMX MBean 接口。
 *
 * <p>暴露 JSON 引擎运行时配置、操作指标与缓存状态，便于运维通过 JConsole / VisualVM / Prometheus 采集。
 *
 * <p><b>ObjectName：</b>{@code com.njydsz.common.json:type=JsonConfigViewer}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JsonConfigViewerMBean {

  /**
   * 获取当前全局配置版本号。
   *
   * <p>每次 {@code JsonConfig.install()} 自增，可用于检测配置热更新是否生效。
   *
   * @return 配置版本号
   */
  long getConfigVersion();

  /**
   * 获取当前全局配置的完整字符串表示。
   *
   * @return 配置详情（含命名策略、writeNulls、maxDepth 等）
   */
  String getConfigDetails();

  /**
   * 获取 Bean 序列化器缓存条目数（Class 维度）。
   *
   * @return 缓存的 Bean 类数量
   */
  int getBeanSerializerCacheSize();

  /**
   * 获取 Bean 字段元数据缓存条目数（Class + NamingStrategy 维度）。
   *
   * @return 字段元数据缓存条目数
   */
  int getFieldMetaCacheSize();

  /**
   * 获取已注册的 JsonModule 模块名称列表。
   *
   * <p>用于验证 SPI 模块是否被正确发现并注册。
   *
   * @return 模块名称列表字符串（逗号分隔），无模块时返回 "none"
   * @since 26.09.01
   */
  String getRegisteredModuleNames();

  /**
   * 获取累计序列化调用次数。
   *
   * @return 序列化调用总次数
   * @since 26.09.01
   */
  long getSerializeCount();

  /**
   * 获取累计序列化耗时（纳秒）。
   *
   * @return 序列化总耗时（纳秒）
   * @since 26.09.01
   */
  long getSerializeTimeNanos();

  /**
   * 获取累计反序列化调用次数。
   *
   * @return 反序列化调用总次数
   * @since 26.09.01
   */
  long getDeserializeCount();

  /**
   * 获取累计反序列化耗时（纳秒）。
   *
   * @return 反序列化总耗时（纳秒）
   * @since 26.09.01
   */
  long getDeserializeTimeNanos();

  /**
   * 清除所有序列化缓存（BeanSerializerCache + SerializerCache）。
   *
   * <p>用于配置热更新后手动失效旧缓存。操作后 {@link #getBeanSerializerCacheSize()} 应返回 0。
   */
  void clearAllCaches();
}
