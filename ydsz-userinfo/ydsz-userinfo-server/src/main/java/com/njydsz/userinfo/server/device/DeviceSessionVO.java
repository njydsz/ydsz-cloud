package com.njydsz.userinfo.server.device;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 设备会话视图对象（P3-2）。
 *
 * <p>展示用户当前活跃的设备会话信息，用于设备管理页面。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class DeviceSessionVO {

  /** 会话标识（access_token 前 8 位掩码，用于前端展示） */
  private String sessionId;

  /** 设备类型编码（web/app/api/unknown） */
  private String deviceType;

  /** 设备类型描述 */
  private String deviceTypeDesc;

  /** 登录 IP */
  private String loginIp;

  /** 登录时间 */
  private LocalDateTime loginTime;

  /** 最后活跃时间 */
  private LocalDateTime lastActiveTime;

  /** 是否为当前会话（用户正在使用的会话） */
  private boolean currentSession;

  /** 设备指纹（User-Agent 摘要） */
  private String deviceFingerprint;

  /** 地理位置（基于 IP 解析，可能为空） */
  private String location;
}
