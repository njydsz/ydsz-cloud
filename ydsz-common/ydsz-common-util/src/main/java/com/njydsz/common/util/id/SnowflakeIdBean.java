package com.njydsz.common.util.id;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 雪花 ID 生成器 Spring 配置类。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 自 26.09.01 起由 {@code UtilAutoConfiguration} 统一注册（通过
 *     {@code AutoConfiguration.imports}，不依赖业务侧组件扫描）。本类与自动装配中的 Bean
 *     方法重复，且在业务主类扫描 {@code com.njydsz} 包时会产生同名 Bean 冲突风险。 请改用自动装配（引入
 *     ydsz-common-util 依赖即生效），需要自定义时声明 {@link WorkerIdAllocator} Bean 或提供自定义
 *     {@code SnowflakeIdGenerator} Bean 覆盖默认注册。 将于下一个大版本移除。
 */
@Deprecated(since = "26.09.01", forRemoval = true)
@Configuration
@ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", matchIfMissing = true)
public class SnowflakeIdBean {

  /**
   * 注册 SnowflakeIdGenerator 作为主 Bean。
   *
   * @param properties Snowflake 配置属性（由 {@code @EnableConfigurationProperties} 注入）
   * @param allocator WorkerId 分配策略链
   * @return SnowflakeIdGenerator 实例
   * @deprecated 由 {@code UtilAutoConfiguration#snowflakeIdGenerator} 取代
   */
  @Deprecated(since = "26.09.01", forRemoval = true)
  @Bean
  @Primary
  public SnowflakeIdGenerator snowflakeIdGenerator(
      SnowflakeProperties properties, WorkerIdAllocator allocator) {
    int sequenceBits =
        properties.getSequenceBits() != null
            ? properties.getSequenceBits()
            : SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS;
    return new SnowflakeIdGenerator(properties, allocator, sequenceBits);
  }
}
