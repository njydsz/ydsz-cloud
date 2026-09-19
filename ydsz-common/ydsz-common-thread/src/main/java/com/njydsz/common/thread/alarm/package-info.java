/**
 * 线程池告警体系 SPI。
 *
 * <p>提供 {@link com.njydsz.common.thread.alarm.AlarmNotifier} 通知器接口、默认日志实现、
 * 周期性评估器及自动配置，对标 Dynamic TP 告警体系的多维度阈值判断和告警去重能力。
 *
 * <p>启用方式：application.yml 中设置 {@code ydsz.thread.alarm.enabled=true}。
 *
 * @since 26.09.19
 */
package com.njydsz.common.thread.alarm;
