package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 接口权限创建/更新 DTO
 *
 * <p>对应 {@code ydsz_sys_api_permission} 表的写入参数，是「接口权限注册中心」创建 / 更新接口的入参载体。
 * 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code apiCode} — 权限码（如 sys:config:list），同租户内唯一
 *   <li>{@code apiName} — 接口名称/描述
 *   <li>{@code httpMethod} — HTTP 方法
 *   <li>{@code urlPattern} — URL 模式
 *   <li>{@code controllerClass} — Controller 完全限定名
 *   <li>{@code methodName} — Controller 方法名
 *   <li>{@code description} — 接口描述
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ApiPermissionDTO {

  private String id;

  @NotBlank(message = "权限码不能为空")
  @Size(max = 128, message = "权限码长度不能超过128")
  private String apiCode;

  @Size(max = 256, message = "接口名称长度不能超过256")
  private String apiName;

  @Size(max = 10, message = "HTTP 方法长度不能超过10")
  private String httpMethod;

  @Size(max = 512, message = "URL 模式长度不能超过512")
  private String urlPattern;

  @Size(max = 512, message = "Controller 类名长度不能超过512")
  private String controllerClass;

  @Size(max = 128, message = "方法名长度不能超过128")
  private String methodName;

  @Size(max = 512, message = "描述长度不能超过512")
  private String description;

  private String status;
}
