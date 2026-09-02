package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户登录历史视图对象。
 *
 * <p>记录用户的每次登录尝试（成功/失败），用于登录审计和安全分析。
 * 敏感信息（密码、Token）不包含在返回字段中。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code userId} — 用户 ID</li>
 *   <li>{@code username} — 登录用户名</li>
 *   <li>{@code loginIp} — 登录来源 IP 地址</li>
 *   <li>{@code loginResult} — 登录结果（SUCCESS/FAILED）</li>
 *   <li>{@code failReason} — 失败原因（成功时为 null，如 PASSWORD_INCORRECT/ACCOUNT_LOCKED）</li>
 *   <li>{@code userAgent} — 浏览器/设备信息</li>
 *   <li>{@code createdAt} — 登录时间</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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
