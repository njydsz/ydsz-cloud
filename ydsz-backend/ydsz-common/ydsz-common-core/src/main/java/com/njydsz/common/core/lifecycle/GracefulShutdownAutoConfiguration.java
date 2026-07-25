package com.njydsz.common.core.lifecycle;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.core.config.CoreProperties;

/**
 * 优雅停机自动配置
 *
 * <p>注册 {@link GracefulShutdownCoordinator} 到 Spring 容器，统一管理所有组件的停机顺序。
 *
 * <p>停机顺序（phase 越大越先停机）：
 * <ul>
 *   <li>Web 层停机钩子（phase = MAX_VALUE）- 停止接收新请求</li>
 *   <li>消息队列消费者（phase = 2000）- 停止消费消息</li>
 *   <li>异步任务执行器（phase = 1000）- 等待任务完成</li>
 *   <li>数据库连接池（phase = 0）- 最后关闭连接</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   core:
 *     graceful-shutdown:
 *       enabled: true
 *       timeout-seconds: 30
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see GracefulShutdownCoordinator
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.core.graceful-shutdown", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GracefulShutdownAutoConfiguration {

    /**
     * 注册优雅停机协调器
     *
     * @param properties 核心配置属性
     * @return GracefulShutdownCoordinator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public GracefulShutdownCoordinator gracefulShutdownCoordinator(CoreProperties properties) {
        int timeoutSeconds = properties.getGracefulShutdown().getTimeoutSeconds();
        return new GracefulShutdownCoordinator(timeoutSeconds);
    }
}
