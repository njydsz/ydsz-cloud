package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 应用注册创建/更新 DTO
 *
 * <p>对应 {@code ydsz_sys_app_info} 表的写入参数，对标 OAuth 2.0 客户端注册模型。 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code
 * id} 必填。
 *
 * <p><b>字段约束：</b>
 *
 * <ul>
 *   <li>{@code appCode} — 应用编码，租户内唯一，最长 64 字符
 *   <li>{@code appName} — 应用名称，最长 128 字符
 *   <li>{@code appKey} — 客户端 ID（{@code client_id}），最长 128 字符
 *   <li>{@code appSecret} — 客户端密钥（{@code client_secret}），BCrypt 加密存储，最长 256 字符
 *   <li>{@code redirectUrl} — 授权回调地址，最长 512 字符，需 URL 合法
 *   <li>{@code scopes} — OAuth2 授权范围（CSV），如 {@code "user.read,order.write"}
 *   <li>{@code boundIps} — IP 绑定白名单（CSV），如 {@code "192.168.1.0/24,10.0.0.1"}，为空表示不限制
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}
 * </ul>
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>{@code appSecret} 在 Service 层 BCrypt 加密后存储，明文不落库
 *   <li>接口权限校验：{@code ydsz:app:create / ydsz:app:update}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class AppInfoDTO {

  private String id;

  @NotBlank(message = "{system.dto.appInfo.appCode.required}")
  @Size(max = 64, message = "{system.dto.appInfo.appCode.max}")
  @Xss(message = "{system.dto.appInfo.appCode.xss}")
  private String appCode;

  @NotBlank(message = "{system.dto.appInfo.appName.required}")
  @Size(max = 128, message = "{system.dto.appInfo.appName.max}")
  @Xss(message = "{system.dto.appInfo.appName.xss}")
  private String appName;

  @NotBlank(message = "{system.dto.appInfo.appKey.required}")
  @Size(max = 128, message = "{system.dto.appInfo.appKey.max}")
  @Xss(message = "{system.dto.appInfo.appKey.xss}")
  private String appKey;

  @Size(max = 256, message = "{system.dto.appInfo.appSecret.max}")
  private String appSecret;

  @Size(max = 512, message = "{system.dto.appInfo.redirectUrl.max}")
  @Xss(message = "{system.dto.appInfo.redirectUrl.xss}")
  private String redirectUrl;

  @Size(max = 512, message = "{system.dto.appInfo.scopes.max}")
  @Xss(message = "{system.dto.appInfo.scopes.xss}")
  private String scopes;

  @Size(max = 512, message = "{system.dto.appInfo.boundIps.max}")
  @Xss(message = "{system.dto.appInfo.boundIps.xss}")
  private String boundIps;

  @Xss(message = "{system.dto.appInfo.description.xss}")
  private String description;

  private String status;
}
