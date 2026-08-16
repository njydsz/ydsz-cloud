package com.njydsz.common.json.spring.boot;

/**
 * YdszJson 配置 JMX MBean 接口。
 *
 * <p>暴露 JSON 引擎运行时配置与缓存状态，便于运维通过 JConsole / VisualVM / Prometheus 采集。
 *
 * <p><b>ObjectName：</b>{@code com.njydsz.common.json:type=JsonConfigViewer}
 *
 * @author ydsz-team
 * @since 1.2.1
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
   * 清除所有序列化缓存（BeanSerializerCache + SerializerCache）。
   *
   * <p>用于配置热更新后手动失效旧缓存。操作后 {@link #getBeanSerializerCacheSize()} 应返回 0。
   */
  void clearAllCaches();
}
