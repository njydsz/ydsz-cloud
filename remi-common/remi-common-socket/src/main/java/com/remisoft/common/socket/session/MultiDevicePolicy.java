package com.remisoft.common.socket.session;

/**
 * 多端登录策略枚举（P1-3）。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum MultiDevicePolicy {

    /** 允许多端同时在线（默认） */
    ALLOW_ALL,

    /** 互斥登录：同一用户仅允许一个设备在线 */
    MUTEX,

    /** 新设备挤占旧设备：新连接建立时踢出旧设备 */
    NEW_REPLACE_OLD
}
