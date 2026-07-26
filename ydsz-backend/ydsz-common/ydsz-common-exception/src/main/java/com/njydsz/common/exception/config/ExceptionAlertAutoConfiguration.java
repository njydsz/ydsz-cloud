package com.njydsz.common.exception.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.alert.ExceptionAlertListener;
import com.njydsz.common.exception.alert.ExceptionAlertPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 异常告警自动配置
 *
 * <p>自动注册 {@link ExceptionAlertPublisher} 并注入所有 {@link ExceptionAlertListener} Bean。
 *
 * <p>通过 {@code ydsz.exception.alert-enabled=true}（默认启用）控制是否注册。
 *
 * <p>配置项：
 * <pre>{@code
 * ydsz:
 *   exception:
 *     alert-enabled: true
 *     alert-dedup-window-seconds: 60    # 同一 errorCode 去重时间窗口
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionAlertPublisher
 * @see ExceptionAlertListener
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.exception", name = "alert-enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExceptionProperties.class)
public class ExceptionAlertAutoConfiguration {

    /**
     * 创建异常告警发布器 Bean
     *
     * @param properties      异常模块配置属性
     * @param listenersProvider 告警监听器列表（可选）
     * @return 异常告警发布器实例
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionAlertPublisher.class)
    public ExceptionAlertPublisher exceptionAlertPublisher(
            ExceptionProperties properties,
            ObjectProvider<List<ExceptionAlertListener>> listenersProvider) {
        ExceptionAlertPublisher publisher = new ExceptionAlertPublisher(
                properties.getAlertDedupWindowSeconds()
        );
        List<ExceptionAlertListener> listeners = listenersProvider.getIfAvailable();
        if (listeners != null) {
            for (ExceptionAlertListener listener : listeners) {
                publisher.addListener(listener);
                log.info("注册异常告警监听器: {}", listener.getClass().getSimpleName());
            }
        }
        log.info("异常告警发布器已初始化 | 监听器数量: {}",
                listeners != null ? listeners.size() : 0);
        return publisher;
    }
}
