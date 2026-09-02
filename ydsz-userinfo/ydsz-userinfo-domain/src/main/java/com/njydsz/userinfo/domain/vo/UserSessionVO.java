package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户会话信息 VO。
 *
 * <p>用于展示用户当前活跃的会话列表，支持管理员查看和强制下线指定设备。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserSessionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 会话 accessToken（脱敏展示） */
  private String accessToken;

  /** 登录时间（ISO 8601 格式） */
  private String loginTime;

  /** 登录 IP */
  private String loginIp;

  /** 设备/浏览器 User-Agent */
  private String userAgent;

  /** 会话过期时间（ISO 8601 格式） */
  private String expireTime;

  /** 设备类型编码（web/app/api/unknown） */
  private String device;

  /** 用户名 */
  private String username;
}
