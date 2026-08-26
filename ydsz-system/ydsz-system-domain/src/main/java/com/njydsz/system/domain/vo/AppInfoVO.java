package com.njydsz.system.domain.vo;

import lombok.Data;

import com.njydsz.common.json.annotation.JsonIgnore;

/**
 * 应用注册 VO
 *
 * <p>对应 {@code ydsz_sys_app_info} 表的展示视图，是「应用注册中心」列表 / 详情接口的返回值类型。 由 {@link
 * com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.AppInfo} 实体转换而来。
 *
 * <p><b>安全约束（关键）：</b>
 *
 * <ul>
 *   <li><b>不暴露</b> {@code appSecret} 字段，BCrypt 哈希<b>永远</b>不出现在 VO 中， 即便数据库被拖库也不可逆
 *   <li>管理后台「查看密钥」入口走单独的 {@code /app/{id}/secret} 接口， 该接口需 {@code ydsz:app:secret:view} 权限码 +
 *       二次密码确认 + 操作审计
 *   <li>列表接口在 {@code YdszJson} 序列化层强制忽略 {@code appSecret} 字段 （如未来误加
 *       {@code @com.njydsz.common.json.annotation.JsonProperty}，需通过单元测试拦截）
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>应用管理列表 / 详情 / 编辑回显
 *   <li>OAuth 2.0 客户端注册信息展示
 *   <li>第三方应用对接时的「应用信息」展示
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AppInfoVO {

  private String id;

  private String appCode;

  private String appName;

  private String appKey;

  /**
   * 应用密钥（BCrypt 哈希），仅供内部密钥校验使用。
   *
   * <p>标记 {@code @JsonIgnore} 确保序列化时不暴露给前端。
   */
  @JsonIgnore
  private String appSecret;

  private String redirectUrl;

  private String scopes;

  private String boundIps;

  private String description;

  private String status;
}
