package com.njydsz.pmis.common.socket.session;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备 Session 信息（P1-3）。
 *
 * <p>记录每个 WebSocket Session 的设备信息，支持多端管理和互斥登录。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSessionInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Session ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 设备标识（可为 null） */
    private String deviceId;

    /** 客户端平台（WEB / IOS / ANDROID / PC） */
    private String platform;

    /** 连接时间戳 */
    private long connectedAt;

    /** 最后活跃时间戳 */
    private long lastActiveAt;
}
