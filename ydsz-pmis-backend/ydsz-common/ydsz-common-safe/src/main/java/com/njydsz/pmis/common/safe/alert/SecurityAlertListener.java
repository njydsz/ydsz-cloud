package com.njydsz.common.safe.alert;


import java.util.ServiceLoader;
/**
 * 安全事件告警监听器 SPI 接口
 *
 * <p>通过 {@link ServiceLoader} 加载所有实现，接收安全事件回调。
 *
 * @since 1.0.0
 * 
 */
public interface SecurityAlertListener {

    /**
     * 安全事件回调
     *
     * @param event 安全事件
     */
    void onSecurityEvent(SecurityEvent event);
}
