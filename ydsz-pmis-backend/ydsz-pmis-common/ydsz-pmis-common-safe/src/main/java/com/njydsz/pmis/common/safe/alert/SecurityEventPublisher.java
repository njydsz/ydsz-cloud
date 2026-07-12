package com.njydsz.pmis.common.safe.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 瀹夊叏浜嬩欢鍙戝竷鍣?
 *
 * <p>閫氳繃 Spring {@link ApplicationEventPublisher} 鍙戝竷浜嬩欢锛?
 * 鍚屾椂閫氳繃 {@link ServiceLoader} 璋冪敤鎵€鏈?SPI 瀹炵幇鐨勭洃鍚櫒銆?
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
     * 鍙戝竷瀹夊叏浜嬩欢
     *
     * @param event 瀹夊叏浜嬩欢
     */
    public void publish(SecurityEvent event) {
        // 鍙戝竷 Spring 搴旂敤浜嬩欢
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(event);
        }

        // 璋冪敤 SPI 鐩戝惉鍣?
        for (SecurityAlertListener listener : spiListeners) {
            try {
                listener.onSecurityEvent(event);
            } catch (Exception e) {
                log.warn("瀹夊叏浜嬩欢鐩戝惉鍣ㄥ鐞嗗紓甯? {}", listener.getClass().getName(), e);
            }
        }
    }

    private List<SecurityAlertListener> loadSpiListeners() {
        List<SecurityAlertListener> listeners = new ArrayList<>();
        ServiceLoader<SecurityAlertListener> loader = ServiceLoader.load(SecurityAlertListener.class);
        for (SecurityAlertListener listener : loader) {
            listeners.add(listener);
            log.info("鍔犺浇瀹夊叏浜嬩欢鍛婅鐩戝惉鍣? {}", listener.getClass().getName());
        }
        return listeners;
    }
}
