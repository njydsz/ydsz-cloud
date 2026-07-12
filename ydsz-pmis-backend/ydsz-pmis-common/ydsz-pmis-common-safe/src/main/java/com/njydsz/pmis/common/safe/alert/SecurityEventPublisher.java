package com.njydsz.pmis.common.safe.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 安全事件发布器
 *
 * <p>通过 Spring {@link ApplicationEventPublisher} 发布事件，
 * 同时通过 {@link ServiceLoader} 调用所有 SPI 实现的监听器。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class SecurityEventPublisher implements ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventPublisher.class);

    private final List<SecurityAlertListener> spiListeners;
    private ApplicationEventPublisher applicationEventPublisher;

    public SecurityEventPublisher() {
        this.spiListeners = loadSpiListeners();
    }

    @Override
    public void setApplicationEventPublisher(@NonNull ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布安全事件
     *
     * @param event 安全事件
     */
    public void publish(SecurityEvent event) {
        // 发布 Spring 应用事件
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(event);
        }

        // 调用 SPI 监听器
        for (SecurityAlertListener listener : spiListeners) {
            try {
                listener.onSecurityEvent(event);
            } catch (Exception e) {
                log.warn("安全事件监听器处理异常: {}", listener.getClass().getName(), e);
            }
        }
    }

    private List<SecurityAlertListener> loadSpiListeners() {
        List<SecurityAlertListener> listeners = new ArrayList<>();
        ServiceLoader<SecurityAlertListener> loader = ServiceLoader.load(SecurityAlertListener.class);
        for (SecurityAlertListener listener : loader) {
            listeners.add(listener);
            log.info("加载安全事件告警监听器: {}", listener.getClass().getName());
        }
        return listeners;
    }
}
