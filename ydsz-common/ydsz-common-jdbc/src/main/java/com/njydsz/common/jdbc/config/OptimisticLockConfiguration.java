package com.njydsz.common.jdbc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.jdbc.interceptor.OptimisticLockInterceptor;

import lombok.Data;

/**
 * 乐观锁拦截器配置类
 *
 * <p>用于配置自定义乐观锁拦截器的行为参数，
 * 包括版本号字段名、默认值、启用状态等。
 *
 * <p>配置示例：
 * <pre>
 * # application.yml
 * ydsz:
 *   jdbc:
 *     optimistic-lock:
 *       enable: true                           # 是否启用乐观锁拦截
 *       revision-column: revision              # 版本号字段名
 *       default-revision-value: 0               # INSERT 时的默认值
 * </pre>
 *
 * <p>与 MyBatis-Plus @Version 注解的对比：
 * <ul>
 *   <li>@Version：需要在实体类字段上添加注解，依赖实体与数据库字段绑定</li>
 *   <li>本配置：纯 SQL 层拦截，不依赖实体字段值，更适合 DTO 更新场景</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see OptimisticLockInterceptor
 */
@Data
@Configuration
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
@ConfigurationProperties(prefix = "ydsz.jdbc.optimistic-lock")
public class OptimisticLockConfiguration {

    /**
     * 是否启用乐观锁拦截
     * <p>默认为 false，需要手动设置为 true 以启用
     */
    private boolean enable = false;

    /**
     * 版本号字段名
     * <p>用于标识数据库表中的版本号列名
     * <p>默认值：revision
     */
    private String revisionColumn = "revision";

    /**
     * INSERT 操作时版本号的默认值
     * <p>当 INSERT 语句未指定版本号时使用的默认值
     * <p>默认值：0（表示初始版本）
     */
    private Long defaultRevisionValue = 0L;
}
