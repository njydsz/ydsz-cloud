package com.njydsz.pmis.common.safe.alert;

/**
 * 安全事件告警监听器 SPI 接口
 *
 * <p>通过 {@link java.util.ServiceLoader} 加载所有实现，接收安全事件回调。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface SecurityAlertListener {

    /**
     * 安全事件回调
     *
     * @param event 安全事件
     */
    void onSecurityEvent(SecurityEvent event);
}
