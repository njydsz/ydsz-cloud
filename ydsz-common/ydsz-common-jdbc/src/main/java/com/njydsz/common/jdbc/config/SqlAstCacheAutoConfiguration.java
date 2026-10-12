package com.njydsz.common.jdbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.monitor.SqlAstCache;

/**
 * SqlAstCache 独立自动配置。
 *
 * <p>独立于 {@link MybatisPlusConfiguration} 创建 SqlAstCache Bean，
 * 避免 {@code MybatisPlusConfiguration} ⟷ {@code SqlAstCache} 的循环依赖
 * （MybatisPlusConfiguration 通过 ObjectProvider 使用 SqlAstCache，
 * 但 bean 方法在同一类中定义导致创建时序冲突）。
 *
 * @author ydsz-team
 * @since 26.10.12
 */
@AutoConfiguration
@AutoConfigureAfter(MybatisPlusConfiguration.class)
public class SqlAstCacheAutoConfiguration {

  @Bean
  public SqlAstCache sqlAstCache() {
    return new SqlAstCache(SqlAstCache.DEFAULT_MAX_SIZE);
  }
}
