package com.njydsz.common.json.spring.boot;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.cache.BeanSerializerCache;
import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.internal.JsonConfig;

/**
 * YdszJson 配置 JMX MBean 实现。
 *
 * <p>将 JSON 引擎内部状态（全局配置版本、命名策略、循环引用策略、SerializerCache 大小、 估算的 ThreadLocal 内存占用）暴露到
 * JMX，便于运维排查配置泄漏、内存异常等问题。
 *
 * <p><b>运维场景：</b>
 *
 * <ul>
 *   <li>配置热更新验证：对比修改前后 {@link #getConfigVersion()} 是否自增
 *   <li>缓存膨胀诊断：通过 {@link #getBeanSerializerCacheSize()} 判断是否有类加载器泄漏
 *   <li>ThreadLocal 泄漏检测：通过 JMX 监控 {@code estimateThreadLocalMemory()}
 * </ul>
 *
 * <p>由 {@link JsonAutoConfiguration.JsonConfigBean} 在 {@code @PostConstruct} 阶段 自动注册到平台
 * MBeanServer，无需业务代码介入。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class JsonConfigViewer implements JsonConfigViewerMBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonConfigViewer.class);

  /** JMX ObjectName */
  private static final String OBJECT_NAME = "com.njydsz.common.json:type=JsonConfigViewer";

  /**
   * 注册 MBean 到平台 MBeanServer。
   *
   * <p>若同一 MBean 已注册（如容器热部署场景），先注销旧实例再注册新实例， 避免 {@link
   * javax.management.InstanceAlreadyExistsException}。
   */
  public void register() {
    try {
      MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      ObjectName objectName = new ObjectName(OBJECT_NAME);
      if (mBeanServer.isRegistered(objectName)) {
        mBeanServer.unregisterMBean(objectName);
      }
      mBeanServer.registerMBean(this, objectName);
    } catch (Exception e) {
      // JMX 注册失败不应阻断应用启动（可能受限于容器安全策略）
      LOGGER.warn(
          "JsonConfigViewer JMX MBean 注册失败，ObjectName={}，reason={}", OBJECT_NAME, e.getMessage());
    }
  }

  /** 从平台 MBeanServer 注销 MBean。 */
  public void unregister() {
    try {
      MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
      ObjectName objectName = new ObjectName(OBJECT_NAME);
      if (mBeanServer.isRegistered(objectName)) {
        mBeanServer.unregisterMBean(objectName);
      }
    } catch (Exception e) {
      LOGGER.warn(
          "JsonConfigViewer JMX MBean 注销失败，ObjectName={}，reason={}", OBJECT_NAME, e.getMessage());
    }
  }

  @Override
  public long getConfigVersion() {
    return JsonConfig.getConfigVersion();
  }

  @Override
  public String getConfigDetails() {
    // P1 修复：读取当前已安装配置（原 copyOf(null) 永远返回默认配置，热更新观测形同虚设）
    JsonConfig config = JsonConfig.getInstance();
    if (config == null) {
      return "JsonConfig not initialized";
    }
    return config.toString();
  }

  @Override
  public int getBeanSerializerCacheSize() {
    return BeanSerializerCache.size();
  }

  @Override
  public int getFieldMetaCacheSize() {
    return SerializerCache.size();
  }

  @Override
  public void clearAllCaches() {
    BeanSerializerCache.clear();
    SerializerCache.clear();
  }
}
