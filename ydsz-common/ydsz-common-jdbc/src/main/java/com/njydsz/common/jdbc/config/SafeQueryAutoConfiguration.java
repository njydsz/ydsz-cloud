package com.njydsz.common.jdbc.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.domain.config.DomainProperties;
import com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor;

/**
 * 安全查询拦截器自动配置（ORDER BY 注入防护 + 深度分页检测）。
 *
 * <p>向 {@link MybatisPlusInterceptor} 注入 {@link SafeQueryInnerInterceptor}， 统一处理：
 *
 * <ul>
 *   <li>ORDER BY 字段安全校验（正则 + 可选白名单）
 *   <li>深度分页检测与拦截
 * </ul>
 *
 * <p>配置项：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     safe-query:
 *       enabled: true                # 启用安全查询拦截（默认 true）
 *       strict-mode: false           # true=拒绝非法排序字段抛异常, false=忽略
 *       order-by-whitelist: []       # 排序字段白名单
 * }</pre>
 *
 * <p>触发条件：
 *
 * <ul>
 *   <li>{@code ydzs.jdbc.safe-query.enabled=true}（默认启用）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SafeQueryInnerInterceptor
 * @see MybatisPlusConfiguration
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(
    prefix = "ydsz.jdbc.safe-query",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({
  JdbcProperties.class,
  SafeQueryProperties.class,
  DomainProperties.class
})
public class SafeQueryAutoConfiguration {

  /**
   * 创建安全查询拦截器 Bean。
   *
   * @param domainProperties 领域配置（深度分页阈值）
   * @param safeQueryProperties 安全查询配置属性
   * @return SafeQueryInnerInterceptor 实例
   */
  @Bean
  @ConditionalOnMissingBean(SafeQueryInnerInterceptor.class)
  public SafeQueryInnerInterceptor safeQueryInnerInterceptor(
      ObjectProvider<DomainProperties> domainProperties,
      ObjectProvider<SafeQueryProperties> safeQueryProperties) {

    SafeQueryInnerInterceptor interceptor =
        new SafeQueryInnerInterceptor(domainProperties.getIfAvailable());

    // 从 JDBC 配置加载安全查询设置
    SafeQueryProperties safeQuery = safeQueryProperties.getIfAvailable();
    if (safeQuery != null) {
      interceptor.setEnabled(safeQuery.isEnabled());
      interceptor.setStrictMode(safeQuery.isStrictMode());
      if (safeQuery.getOrderByWhitelist() != null) {
        interceptor.setOrderByWhitelist(safeQuery.getOrderByWhitelist());
      }
    }

    log.info("安全查询拦截器已启用 (strictMode={})", interceptor.isStrictMode());
    return interceptor;
  }

  /**
   * 将安全查询拦截器注册到 MyBatis-Plus 拦截器链。
   *
   * <p>本方法只是借助 {@code @Bean} 的生命周期完成注册副作用，返回值本身没有调用方使用。
   *
   * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
   * @param safeQueryInterceptor SafeQueryInnerInterceptor 实例
   * @return 实际生效的 {@code SafeQueryInnerInterceptor}：链中已存在同类型拦截器时返回链上的旧实例（本次不重复注册），
   *     否则返回刚注册进链的 {@code safeQueryInterceptor}；不会为 {@code null}
   */
  @Bean
  public Object safeQueryInterceptorRegistration(
      MybatisPlusInterceptor mybatisPlusInterceptor,
      SafeQueryInnerInterceptor safeQueryInterceptor) {
    // 查找是否已存在
    for (InnerInterceptor inner : mybatisPlusInterceptor.getInterceptors()) {
      if (inner instanceof SafeQueryInnerInterceptor) {
        return inner; // 已存在，跳过注册
      }
    }
    // 注册到拦截器链（放在靠后位置，让其他拦截器先处理）
    mybatisPlusInterceptor.addInnerInterceptor(safeQueryInterceptor);
    return safeQueryInterceptor;
  }
}
