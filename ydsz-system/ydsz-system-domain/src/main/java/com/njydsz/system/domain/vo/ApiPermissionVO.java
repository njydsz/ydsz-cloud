package com.njydsz.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 接口权限 VO（视图对象）
 *
 * <p>对应 {@code ydsz_sys_api_permission} 表的展示视图，是「接口权限注册中心」列表 / 详情接口的响应载体。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code apiCode} — 权限码（如 sys:config:list）
 *   <li>{@code httpMethod} — HTTP 方法
 *   <li>{@code urlPattern} — URL 模式
 *   <li>{@code controllerClass} — Controller 完全限定名
 *   <li>{@code methodName} — Controller 方法名
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ApiPermissionVO {

  private String id;

  private String apiCode;

  private String apiName;

  private String httpMethod;

  private String urlPattern;

  private String controllerClass;

  private String methodName;

  private String description;

  private String status;

  private String createdBy;

  private String createdAt;

  private String updatedBy;

  private String updatedAt;
}
