package com.njydsz.common.util.id;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 雪花 ID 生成器 Spring 配置类。
 *
 * <p>将核心算法类 {@link SnowflakeIdGenerator} 注册为 Spring Bean，
 * 使其支持依赖注入和 Spring IoC 容器生命周期管理。
 *
 * <p>注册条件：
 * <ul>
 *   <li>{@code ydsz.util.snowflake.enabled=true}（或缺省，matchIfMissing=true）时注册</li>
 *   <li>{@code ydsz.util.snowflake.enabled=false} 时跳过注册</li>
 * </ul>
 *
 * <p>sequenceBits 配置优先级：
 * <ol>
 *   <li>{@code ydsz.util.snowflake.sequence-bits} 显式配置</li>
 *   <li>默认值 {@value SnowflakeIdGenerator#DEFAULT_SEQUENCE_BITS} 位</li>
 * </ol>
 *
 * <p>业务方若需自定义序列号位数，可通过 application.yml 配置：
 * <pre>{@code
 * ydsz:
 *   util:
 *     snowflake:
 *       sequence-bits: 10  # 每毫秒 1024 个 ID
 * }</pre>
 *
 * @author ydsz-team
 * @since 4.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", matchIfMissing = true)
public class SnowflakeIdBean {

    /**
     * 注册 SnowflakeIdGenerator 作为主 Bean。
     *
     * <p>使用 {@link WorkerIdAllocator} 策略链自动分配 workerId，
     * 支持显式配置覆盖（{@code ydsz.util.snowflake.worker-id}）。
     *
     * @param properties Snowflake 配置属性（由 {@code @EnableConfigurationProperties} 注入）
     * @param allocator  WorkerId 分配策略链
     * @return SnowflakeIdGenerator 实例
     */
    @Bean
    @Primary
    public SnowflakeIdGenerator snowflakeIdGenerator(SnowflakeProperties properties,
                                                      WorkerIdAllocator allocator) {
        int sequenceBits = properties.getSequenceBits() != null
                ? properties.getSequenceBits()
                : SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS;
        return new SnowflakeIdGenerator(properties, allocator, sequenceBits);
    }
}
















