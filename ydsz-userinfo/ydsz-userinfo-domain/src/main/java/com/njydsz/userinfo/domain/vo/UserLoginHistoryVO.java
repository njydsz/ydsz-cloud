package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户登录历史 VO，用于 Controller 返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserLoginHistoryVO {

  /** 记录唯一标识 */
  private String id;

  /** 用户 ID */
  private String userId;

  /** 用户名 */
  private String username;

  /** 登录 IP 地址 */
  private String loginIp;

  /** 登录结果：SUCCESS / FAILED */
  private String loginResult;

  /** 失败原因（成功时为 null） */
  private String failReason;

  /** 用户代理（浏览器/设备信息） */
  private String userAgent;

  /** 登录时间 */
  private LocalDateTime createdAt;
}
