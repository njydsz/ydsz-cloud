package com.njydsz.common.jdbc.datasource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.njydsz.common.jdbc.datasource.hint.HintManager;
import com.njydsz.common.jdbc.datasource.hint.HintType;

/**
 * 动态路由数据源
 *
 * <p>继承 {@link AbstractRoutingDataSource}，根据 {@link DynamicDataSourceContextHolder}
 * 中的数据源名称动态路由到目标数据源。
 *
 * <p>特性：
 *
 * <ul>
 *   <li>支持运行时动态添加/移除数据源
 *   <li>栈式嵌套切换（支持方法级覆盖类级）
 *   <li>未指定数据源时使用默认数据源
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

  private final Map<Object, DataSource> dataSourceMap = new ConcurrentHashMap<>();
  private Object defaultDataSourceKey;

  /**
   * 读写锁，保护 addDataSource / removeDataSource 中 dataSourceMap 修改 + setTargetDataSources 全量替换的复合操作原子性。
   */
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

  public DynamicRoutingDataSource() {
    super();
    setLenientFallback(true);
  }

  /**
   * 确定当前线程的数据源路由 key。
   *
   * <p>路由优先级（从高到低）：
   *
   * <ol>
   *   <li>{@link HintManager} 强制路由 Hint（编程式，最高优先级）
   *   <li>{@link DynamicDataSourceContextHolder} 中的显式数据源（@DS 注解 / 读写分离）
   *   <li>默认数据源
   * </ol>
   *
   * <p><b>读写分离说明：</b>自 26.09.01 起，读写分离能力委托 dynamic-datasource 内置实现， 不再使用自研拦截器。{@link
   * DynamicDataSourceContextHolder} 中的从库选择由 dynamic-datasource 自动处理。
   *
   * @return 数据源路由 key；determineCurrentLookupKey 契约中 null 表示使用默认数据源
   */
  @Override
  protected Object determineCurrentLookupKey() {
    // HintManager 强制路由最高优先级：覆盖事务上下文和 @DS 注解
    var hint = HintManager.get().orElse(null);
    if (hint != null) {
      if (hint.getType() == HintType.CUSTOM) {
        log.debug("HintManager 强制路由到数据源: {}", hint.getDsName());
        return hint.getDsName();
      }
      if (hint.getType() == HintType.MASTER) {
        log.debug("HintManager 强制路由到主库");
        if (defaultDataSourceKey != null) {
          return defaultDataSourceKey;
        }
      }
      if (hint.getType() == HintType.SLAVE) {
        // SLAVE 语义：优先使用 holder 中已选中的从库（由 dynamic-datasource
        // 完成负载均衡后 push）；holder 为空时返回 null 由默认数据源兜底。
        // 注意：本层不负责从库选择，负载均衡由 dynamic-datasource 统一处理。
        String pushedDs = DynamicDataSourceContextHolder.peek();
        if (pushedDs != null) {
          log.debug("HintManager 强制路由到从库: {}", pushedDs);
          return pushedDs;
        }
        log.debug("HintManager 强制路由到从库，但 holder 无显式从库，回退默认数据源");
        return null;
      }
    }

    String ds = DynamicDataSourceContextHolder.peek();
    if (ds == null) {
      return defaultDataSourceKey;
    }
    return ds;
  }

  /**
   * 重写父类方法，同步维护内部 {@link #dataSourceMap}。
   *
   * <p>注意：Spring 6+ 中父类 {@code setTargetDataSources} 签名为 {@code Map<Object, Object>}，此处保持签名一致以正确覆盖。
   *
   * @param targetDataSources 目标数据源映射（value 实际为 {@link DataSource} 类型）
   */
  @Override
  public void setTargetDataSources(Map<Object, Object> targetDataSources) {
    super.setTargetDataSources(targetDataSources);
    if (targetDataSources != null) {
      targetDataSources.forEach(
          (k, v) -> {
            if (v instanceof DataSource) {
              this.dataSourceMap.put(k, (DataSource) v);
            }
          });
    }
  }

  @Override
  public void setDefaultTargetDataSource(Object defaultTargetDataSource) {
    super.setDefaultTargetDataSource(defaultTargetDataSource);
    this.defaultDataSourceKey = defaultTargetDataSource;
  }

  /**
   * 动态添加数据源
   *
   * @param key 数据源键
   * @param dataSource 数据源实例
   */
  public void addDataSource(Object key, DataSource dataSource) {
    rwLock.writeLock().lock();
    try {
      dataSourceMap.put(key, dataSource);
      super.setTargetDataSources(castToTargetMap(dataSourceMap));
      log.info("动态添加数据源: {}", key);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * 动态移除数据源
   *
   * @param key 数据源键
   */
  public void removeDataSource(Object key) {
    if (key.equals(defaultDataSourceKey)) {
      throw new IllegalArgumentException("不能移除默认数据源: " + key);
    }
    rwLock.writeLock().lock();
    try {
      dataSourceMap.remove(key);
      super.setTargetDataSources(castToTargetMap(dataSourceMap));
      log.info("动态移除数据源: {}", key);
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * 将 {@code Map<Object, DataSource>} 安全转换为父类要求的 {@code Map<Object, Object>}。
   *
   * <p>通过新建 {@code HashMap<Object, Object>} 装载原 Map 的 entry，利用 Java 泛型 协变特性（{@code DataSource} 是
   * {@code Object} 的子类）避免 unchecked 警告。
   *
   * @param source 原始数据源映射
   * @return 父类要求的 Map 形式
   */
  private static Map<Object, Object> castToTargetMap(Map<Object, DataSource> source) {
    Map<Object, Object> result = new HashMap<>(source.size());
    for (Map.Entry<Object, DataSource> entry : source.entrySet()) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * 获取所有已注册的数据源
   *
   * @return 数据源映射（不可变）
   */
  public Map<Object, DataSource> getDataSources() {
    return Map.copyOf(dataSourceMap);
  }
}
