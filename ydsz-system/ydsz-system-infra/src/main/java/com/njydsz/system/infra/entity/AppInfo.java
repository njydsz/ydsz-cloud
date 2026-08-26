package com.njydsz.system.infra.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;



/**
  * 应用信息实体
 *
 * <p>对应数据库表 {@code ydsz_sys_app_info}，存储 OAuth2 客户端应用注册信息。 每个接入方（ISV / 第三方系统 / 内部子应用）需先注册一条 AppInfo
 * 记录获取 {@code clientId} / {@code clientSecret}，再通过 {@code /oauth2/token} 端点 换取访问令牌。
 *
 * <p><b>字段语义澄清（P1-7）：</b>
 *
 * <ul>
 *   <li>{@code appCode} — 应用业务编码（对外展示 / 业务标识），唯一索引 {@code uk_app_code} 保证全局唯一
 *   <li>{@code appKey} — 应用唯一标识，<b>认证查询入口</b>（{@code validateClient} 按 {@code appKey} 查询），
 *       语义等价 OAuth2 {@code client_id}，租户内唯一（{@code uk_tenant_app_key}）
 *   <li>{@code appSecret} — 应用密钥，语义等价 OAuth2 {@code client_secret}，BCrypt 哈希后存储（不可逆）
 * </ul>
 *
 * <p><b>字段安全分级：</b>
 *
 * <ul>
 *   <li><b>明文返回：</b>{@code appCode} / {@code appName} / {@code redirectUrl} / {@code description}
 *   <li><b>脱敏返回：</b>{@code appKey} / {@code appSecret}（{@code SensitiveType.PASSWORD}），
 *       仅在「创建/重置密钥」时返回明文
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_app_code}（{@code app_code}）与 {@code uk_tenant_app_key}（{@code tenant_id},
 * {@code app_key}），分别加速编码去重与认证查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_sys_app_info")
public class AppInfo extends MpBaseEntity<String> {

  /** 应用业务编码（对外展示 / 业务标识，全局唯一） */
  private String appCode;

  /** 应用名称 */
  private String appName;

  /** 应用唯一标识（认证查询入口，语义等价 OAuth2 client_id，租户内唯一） */
  private String appKey;

  /** 应用安全密钥（BCrypt 哈希存储，语义等价 OAuth2 client_secret） */
  private String appSecret;

  /** OAuth2 授权回调地址 */
  private String redirectUrl;

  /**
   * OAuth2 授权范围（CSV 格式，如 {@code "user.read,order.write"}）。
   *
   * <p>用于限制应用可访问的 API 权限范围，遵循最小权限原则。
   */
  private String scopes;

  /**
   * IP 绑定白名单（CSV 格式，如 {@code "192.168.1.0/24,10.0.0.1"}）。
   *
   * <p>仅允许白名单 IP 调用该应用的 API，增强安全性。为空表示不限制 IP。
   */
  private String boundIps;

  /** 应用描述 */
  private String description;
}
