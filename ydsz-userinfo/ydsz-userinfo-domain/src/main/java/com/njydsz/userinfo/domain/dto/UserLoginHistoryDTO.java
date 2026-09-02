package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 用户登录历史 DTO。
 *
 * <p>用于保存登录历史记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserLoginHistoryDTO implements Serializable {

  private static final long serialVersionUID = 1L;

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
}
